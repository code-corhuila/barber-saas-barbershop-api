package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Created;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.InternalBarbershopUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.IdGenerator;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public class OnboardBarbershops implements InternalBarbershopUseCases {

    static final String CREATE_OPERATION = "POST /internal/v1/barbershops";
    private static final Page.Request ANY = new Page.Request(1, 1);

    private final BarbershopRepository barbershops;
    private final BarberRepository barbers;
    private final IdGenerator ids;
    private final Clock clock;

    public OnboardBarbershops(BarbershopRepository barbershops, BarberRepository barbers, IdGenerator ids,
                              Clock clock) {
        this.barbershops = barbershops;
        this.barbers = barbers;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Created<Barbershop> create(Caller caller, NewBarbershop data, String idempotencyKey) {
        caller.requireService(WORKFLOW);
        // No tenant exists yet: the calling service takes its place in the hash.
        String hash = RequestHash.of(caller.subject(), data.name(), data.address(), data.city(), data.latitude(),
                data.longitude(), data.phone());

        Optional<Idempotency.Stored> stored = barbershops.findKey(idempotencyKey, CREATE_OPERATION);
        if (stored.isPresent()) {
            if (!stored.get().requestHash().equals(hash)) {
                throw new IdempotencyKeyReused();
            }
            return new Created<>(barbershops.findById(stored.get().resourceId())
                    .orElseThrow(IdempotencyKeyReused::new), false);
        }
        Barbershop barbershop = Barbershop.register(ids.next(), data.name(), data.address(), data.city(),
                data.latitude(), data.longitude(), data.phone(), clock.instant());
        barbershops.saveNew(barbershop, new Idempotency.Key(idempotencyKey, CREATE_OPERATION, hash));
        return new Created<>(barbershop, true);
    }

    @Override
    public void remove(Caller caller, UUID id) {
        caller.requireService(WORKFLOW);
        Optional<Barbershop> found = barbershops.findById(id);
        if (found.isEmpty()) {
            return;
        }
        found.get().requireRemovable(barbers.page(id, null, ANY).total() > 0);
        // The delete checks the rule again in the same statement: a barber created meanwhile keeps it.
        if (!barbershops.deleteIfRemovable(id) && barbershops.findById(id).isPresent()) {
            throw new BusinessRuleViolation("The barbershop changed while it was being removed");
        }
    }
}
