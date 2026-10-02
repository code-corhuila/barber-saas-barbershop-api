package co.edu.corhuila.barbersaas.barbershop.application.port.out;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.util.Optional;
import java.util.UUID;

/** Persistence of barbershop.service. Every read is scoped by the barbershop. */
public interface ServiceRepository {

    /** {@code active} null: active and inactive. Most recent first. */
    Page<Service> page(UUID barbershopId, Boolean active, Page.Request page);

    Optional<Service> findById(UUID barbershopId, UUID id);

    Optional<Idempotency.Stored> findKey(String key, String operation);

    /** Writes the service and its idempotency key in ONE transaction. */
    void saveNew(Service service, Idempotency.Key key);

    void update(Service service);
}
