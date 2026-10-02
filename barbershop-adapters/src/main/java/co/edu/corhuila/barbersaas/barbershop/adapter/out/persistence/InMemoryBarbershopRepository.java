package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Used when DATABASE_URL is empty: the service starts and its HTTP contract can be tested without a database. */
public class InMemoryBarbershopRepository implements BarbershopRepository {

    private final Map<UUID, Barbershop> rows = new ConcurrentHashMap<>();

    /** Barbershops are created by platform-admin (OQ-10); without a database, tests seed them here. */
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
}
