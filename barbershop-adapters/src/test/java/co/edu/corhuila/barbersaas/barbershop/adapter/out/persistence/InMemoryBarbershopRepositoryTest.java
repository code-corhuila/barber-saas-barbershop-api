package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopChanges;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryBarbershopRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");
    private final InMemoryBarbershopRepository repository = new InMemoryBarbershopRepository();

    private Barbershop at(String name, String lat, String lng, Instant created) {
        Barbershop b = Barbershop.register(UUID.randomUUID(), name, "Neiva", created);
        if (lat != null) {
            b = b.edit(BarbershopChanges.none().withLatitude(Optional.of(new BigDecimal(lat)))
                    .withLongitude(Optional.of(new BigDecimal(lng))), created);
        }
        repository.put(b);
        return b;
    }

    @Test
    void with_coordinates_the_closest_come_first_and_those_without_them_last() {
        at("Far", "2.95", "-75.30", NOW);
        at("Unknown", null, null, NOW);
        at("Near", "2.928", "-75.282", NOW);

        List<String> names = repository.searchVisible(
                new Search(null, new BigDecimal("2.9273"), new BigDecimal("-75.2819")), new Page.Request(1, 10))
                .items().stream().map(Barbershop::name).toList();

        assertEquals(List.of("Near", "Far", "Unknown"), names);
    }

    @Test
    void without_coordinates_the_most_recent_come_first() {
        at("Old", null, null, NOW);
        at("New", null, null, NOW.plusSeconds(60));

        assertEquals("New", repository.searchVisible(new Search(null, null, null), new Page.Request(1, 10))
                .items().get(0).name());
    }
}
