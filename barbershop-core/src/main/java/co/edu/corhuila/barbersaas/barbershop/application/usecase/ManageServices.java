package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Created;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ServiceUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.IdGenerator;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.ServiceRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

public class ManageServices implements ServiceUseCases {

    static final String CREATE_OPERATION = "POST /api/v1/services";

    private final ServiceRepository services;
    private final IdGenerator ids;
    private final Clock clock;

    public ManageServices(ServiceRepository services, IdGenerator ids, Clock clock) {
        this.services = services;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Page<Service> list(Caller caller, Boolean active, Page.Request page) {
        caller.require(Role.ADMIN_BARBERSHOP, Role.BARBER, Role.CLIENT);
        return services.page(caller.tenant(), caller.is(Role.ADMIN_BARBERSHOP) ? active : Boolean.TRUE, page);
    }

    @Override
    public Service get(Caller caller, UUID id) {
        caller.require(Role.ADMIN_BARBERSHOP, Role.BARBER, Role.CLIENT);
        return services.findById(caller.tenant(), id)
                .filter(s -> s.active() || caller.is(Role.ADMIN_BARBERSHOP))
                .orElseThrow(() -> new NotFound("Service"));
    }

    @Override
    public Created<Service> create(Caller caller, ServiceData data, String idempotencyKey) {
        caller.require(Role.ADMIN_BARBERSHOP);
        UUID tenant = caller.tenant();
        String hash = RequestHash.of(tenant, data.name(), data.description(), data.durationMinutes(), data.priceCents());

        // A retry with the same key returns the first service; it never creates a second one.
        Optional<Idempotency.Stored> stored = services.findKey(idempotencyKey, CREATE_OPERATION);
        if (stored.isPresent()) {
            if (!stored.get().requestHash().equals(hash)) {
                throw new IdempotencyKeyReused();
            }
            return new Created<>(services.findById(tenant, stored.get().resourceId())
                    .orElseThrow(IdempotencyKeyReused::new), false);
        }
        Service service = Service.create(ids.next(), tenant, data.name(), data.description(), data.durationMinutes(),
                data.priceCents(), clock.instant());
        services.saveNew(service, new Idempotency.Key(idempotencyKey, CREATE_OPERATION, hash));
        return new Created<>(service, true);
    }

    @Override
    public Service update(Caller caller, UUID id, ServiceData data, Optional<Boolean> active) {
        caller.require(Role.ADMIN_BARBERSHOP);
        Service edited = services.findById(caller.tenant(), id)
                .orElseThrow(() -> new NotFound("Service"))
                .edit(data.name(), data.description(), data.durationMinutes(), data.priceCents(), active);
        services.update(edited);
        return edited;
    }
}
