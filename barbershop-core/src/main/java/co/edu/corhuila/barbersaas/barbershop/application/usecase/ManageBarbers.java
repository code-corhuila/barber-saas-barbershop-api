package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarberUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Created;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.IdGenerator;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Plans;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Users;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Users.User;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * A new profile reads its user once from identity-auth (DEC-SHOP-04, ADR-014): it checks the user
 * and keeps a copy of the name and photo, so no read of a barber ever calls identity-auth. Before that,
 * the barbershop's plan is read from platform-admin to apply its barber limit (DEC-SHOP-07).
 */
public class ManageBarbers implements BarberUseCases {

    static final String CREATE_OPERATION = "POST /api/v1/barbers";
    static final String ADD_SPECIALTY_OPERATION = "POST /api/v1/barbers/{id}/specialties";

    private static final System.Logger LOG = System.getLogger(ManageBarbers.class.getName());

    private final BarberRepository barbers;
    private final BarbershopRepository barbershops;
    private final Users users;
    private final Plans plans;
    private final IdGenerator ids;

    public ManageBarbers(BarberRepository barbers, BarbershopRepository barbershops, Users users, Plans plans,
                         IdGenerator ids) {
        this.barbers = barbers;
        this.barbershops = barbershops;
        this.users = users;
        this.plans = plans;
        this.ids = ids;
    }

    @Override
    public Page<BarberProfile> list(Caller caller, String specialty, Page.Request page) {
        caller.require(Role.ADMIN_BARBERSHOP, Role.BARBER, Role.CLIENT);
        return barbers.page(caller.tenant(), specialty, page);
    }

    @Override
    public BarberProfile get(Caller caller, UUID id) {
        caller.require(Role.ADMIN_BARBERSHOP, Role.BARBER, Role.CLIENT);
        return find(caller, id);
    }

    @Override
    public Created<BarberProfile> create(Caller caller, NewProfile p, String idempotencyKey) {
        caller.require(Role.ADMIN_BARBERSHOP);
        UUID tenant = caller.tenant();
        String hash = RequestHash.of(tenant, p.userId(), p.experienceYears(), p.bio());
        Optional<Created<BarberProfile>> retry = retry(barbers.findKey(idempotencyKey, CREATE_OPERATION), hash,
                id -> barbers.findById(tenant, id));
        if (retry.isPresent()) {
            return retry.get();
        }
        if (barbers.existsForUser(p.userId())) {
            throw new BusinessRuleViolation("The user already has a barber profile");
        }
        requireRoomForAnotherBarber(tenant);
        User user = barberOf(tenant, p.userId());
        BarberProfile profile = BarberProfile.create(ids.next(), tenant, p.userId(), user.fullName(),
                user.profilePhotoUrl(), p.experienceYears(), p.bio());
        try {
            barbers.saveNew(profile, new Idempotency.Key(idempotencyKey, CREATE_OPERATION, hash));
        } catch (BarberRepository.UserAlreadyHasProfile e) {
            throw new BusinessRuleViolation(e.getMessage());
        }
        return new Created<>(profile, true);
    }

    @Override
    public BarberProfile edit(Caller caller, UUID id, Optional<Integer> experienceYears, Optional<String> bio) {
        BarberProfile edited = owned(caller, id).edit(experienceYears, bio);
        barbers.update(edited);
        return edited;
    }

    @Override
    public Page<BarberSpecialty> specialties(Caller caller, UUID barberProfileId, Page.Request page) {
        return barbers.specialties(get(caller, barberProfileId).id(), page);
    }

    @Override
    public Created<BarberSpecialty> addSpecialty(Caller caller, UUID barberProfileId, String name,
                                                 String idempotencyKey) {
        BarberProfile profile = owned(caller, barberProfileId);
        String hash = RequestHash.of(caller.tenant(), profile.id(), name);
        Optional<Created<BarberSpecialty>> retry = retry(barbers.findKey(idempotencyKey, ADD_SPECIALTY_OPERATION),
                hash, id -> barbers.findSpecialty(profile.id(), id));
        if (retry.isPresent()) {
            return retry.get();
        }
        BarberSpecialty specialty = BarberSpecialty.create(ids.next(), profile.id(), name);
        barbers.saveNewSpecialty(specialty, new Idempotency.Key(idempotencyKey, ADD_SPECIALTY_OPERATION, hash));
        return new Created<>(specialty, true);
    }

    @Override
    public void removeSpecialty(Caller caller, UUID barberProfileId, UUID specialtyId) {
        BarberProfile profile = owned(caller, barberProfileId);
        BarberSpecialty specialty = barbers.findSpecialty(profile.id(), specialtyId)
                .orElseThrow(() -> new NotFound("Specialty"));
        barbers.deleteSpecialty(specialty.id());
    }

    /**
     * 404 when identity-auth does not know the user or it belongs to another barbershop (the same answer,
     * DEC-SHOP-01); 422 when it is not an active BARBER. Users.Unavailable (503) creates nothing.
     */
    private User barberOf(UUID tenant, UUID userId) {
        User user = users.find(userId)
                .filter(u -> tenant.equals(u.barbershopId()))
                .orElseThrow(() -> new NotFound("User"));
        if (!"BARBER".equals(user.role()) || !user.active()) {
            throw new BusinessRuleViolation("The user is not an active barber");
        }
        return user;
    }

    /**
     * 422 when the barbershop already has the barbers its plan allows; Plans.Unavailable (503) creates
     * nothing. A barbershop without a plan, or whose plan platform-admin does not know, should not exist:
     * it is not limited and the case is logged.
     */
    private void requireRoomForAnotherBarber(UUID tenant) {
        Optional<Barbershop> barbershop = barbershops.findById(tenant);
        UUID planId = barbershop.map(Barbershop::planId).orElse(null);
        if (planId == null) {
            LOG.log(System.Logger.Level.WARNING, "barbershop {0} has no plan: its barbers are not limited", tenant);
            return;
        }
        Optional<Plans.Plan> plan = plans.find(planId);
        if (plan.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "plan {0} of barbershop {1} is unknown to platform-admin: "
                    + "its barbers are not limited", planId, tenant);
            return;
        }
        barbershop.get().requireRoomForAnotherBarber(plan.get().maxBarbers());
    }

    private BarberProfile find(Caller caller, UUID id) {
        return barbers.findById(caller.tenant(), id).orElseThrow(() -> new NotFound("Barber profile"));
    }

    /** ADMIN_BARBERSHOP may change any profile of their barbershop; a BARBER only their own. */
    private BarberProfile owned(Caller caller, UUID id) {
        caller.require(Role.ADMIN_BARBERSHOP, Role.BARBER);
        BarberProfile profile = find(caller, id);
        if (caller.is(Role.BARBER) && !profile.belongsTo(caller.userId())) {
            throw new Forbidden("A barber can only change their own profile");
        }
        return profile;
    }

    /** A stored key with the same hash returns the first resource; with another hash, 422. */
    private static <T> Optional<Created<T>> retry(Optional<Idempotency.Stored> stored, String hash,
                                                 Function<UUID, Optional<T>> load) {
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        if (!stored.get().requestHash().equals(hash)) {
            throw new IdempotencyKeyReused();
        }
        return Optional.of(new Created<>(load.apply(stored.get().resourceId())
                .orElseThrow(IdempotencyKeyReused::new), false));
    }
}
