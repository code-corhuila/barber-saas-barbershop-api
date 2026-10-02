package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.ServiceRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Used when DATABASE_URL is empty: the service starts and its HTTP contract can be tested without a database. */
public class InMemoryServiceRepository implements ServiceRepository {

    private final Map<UUID, Service> rows = new ConcurrentHashMap<>();
    private final Map<String, Idempotency.Stored> keys = new ConcurrentHashMap<>();

    @Override
    public Page<Service> page(UUID barbershopId, Boolean active, Page.Request page) {
        return Page.of(rows.values().stream()
                .filter(s -> s.barbershopId().equals(barbershopId))
                .filter(s -> active == null || s.active() == active)
                .sorted(Comparator.comparing(Service::createdAt).reversed().thenComparing(Service::id))
                .toList(), page);
    }

    @Override
    public Optional<Service> findById(UUID barbershopId, UUID id) {
        return Optional.ofNullable(rows.get(id)).filter(s -> s.barbershopId().equals(barbershopId));
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return Optional.ofNullable(keys.get(operation + " " + key));
    }

    @Override
    public synchronized void saveNew(Service service, Idempotency.Key key) {
        rows.put(service.id(), service);
        keys.put(key.operation() + " " + key.key(), new Idempotency.Stored(service.id(), key.requestHash()));
    }

    @Override
    public void update(Service service) {
        rows.put(service.id(), service);
    }
}
