package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Used when DATABASE_URL is empty: the service starts and its HTTP contract can be tested without a database. */
public class InMemoryBarbershopRepository implements BarbershopRepository {

    private final Map<UUID, Barbershop> rows = new ConcurrentHashMap<>();
    private final Map<String, Idempotency.Stored> keys = new ConcurrentHashMap<>();
    private final BarberRepository barbers;

    /** The barbers are asked only to refuse removing a barbershop that has them, as the SQL does. */
    public InMemoryBarbershopRepository(BarberRepository barbers) {
        this.barbers = barbers;
    }

    /** Tests seed barbershops here without going through the onboarding saga. */
    public void put(Barbershop barbershop) {
        rows.put(barbershop.id(), barbershop);
    }

    @Override
    public Page<Barbershop> searchVisible(Search search, Page.Request page) {
        Comparator<Barbershop> order = search.latitude() == null
                ? Comparator.comparing(Barbershop::createdAt).reversed()
                : Comparator.comparingDouble(b -> Distance.squared(b.latitude(), b.longitude(),
                        search.latitude(), search.longitude()));
        return Page.of(rows.values().stream()
                .filter(b -> b.status().visible())
                .filter(b -> search.city() == null || b.city().equalsIgnoreCase(search.city()))
                .sorted(order.thenComparing(Barbershop::id))
                .toList(), page);
    }

    @Override
    public Optional<Barbershop> findById(UUID id) {
        return Optional.ofNullable(rows.get(id));
    }

    @Override
    public void update(Barbershop barbershop) {
        rows.put(barbershop.id(), barbershop);
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return Optional.ofNullable(keys.get(operation + " " + key));
    }

    @Override
    public synchronized void saveNew(Barbershop barbershop, Idempotency.Key key) {
        rows.put(barbershop.id(), barbershop);
        keys.put(key.operation() + " " + key.key(), new Idempotency.Stored(barbershop.id(), key.requestHash()));
    }

    @Override
    public synchronized boolean deleteIfRemovable(UUID id) {
        Barbershop b = rows.get(id);
        if (b == null || b.status() != BarbershopStatus.TRIAL || barbers.page(id, null, new Page.Request(1, 1)).total() > 0) {
            return false;
        }
        rows.remove(id);
        return true;
    }
}
