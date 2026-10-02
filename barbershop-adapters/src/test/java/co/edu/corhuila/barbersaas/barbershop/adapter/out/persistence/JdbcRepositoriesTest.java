package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository.UserAlreadyHasProfile;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs against a real PostgreSQL migrated by barber-saas-barbershop-db, connected as barbershop_app
 * (never the administrator). It does not create the schema: set TEST_DATABASE_URL, TEST_DATABASE_USER
 * and TEST_DATABASE_PASSWORD to run it; without them it is skipped.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcRepositoriesTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);
    private static final Page.Request FIRST = new Page.Request(1, 50);

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(System.getenv("TEST_DATABASE_URL"),
            System.getenv("TEST_DATABASE_USER"), System.getenv("TEST_DATABASE_PASSWORD")));
    private final TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    private final JdbcBarbershopRepository barbershops = new JdbcBarbershopRepository(jdbc);
    private final JdbcServiceRepository services = new JdbcServiceRepository(jdbc, tx);
    private final JdbcBarberRepository barbers = new JdbcBarberRepository(jdbc, tx);

    /** Barbershops are created by platform-admin through this service (OQ-10); the test inserts one directly. */
    private Barbershop insertBarbershop(String city, String lat, String lng) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO barbershop.barbershop (id, name, city, latitude, longitude, trial_ends_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", id, "Shop " + id, city,
                lat == null ? null : new BigDecimal(lat), lng == null ? null : new BigDecimal(lng),
                Timestamp.from(NOW.plus(60, ChronoUnit.DAYS)));
        return barbershops.findById(id).orElseThrow();
    }

    private static Idempotency.Key key() {
        return new Idempotency.Key("it-" + UUID.randomUUID(), "TEST", "hash");
    }

    @Test
    void the_search_filters_by_city_and_sorts_by_distance() {
        String city = "City-" + UUID.randomUUID();
        Barbershop far = insertBarbershop(city, "2.95", "-75.30");
        Barbershop near = insertBarbershop(city, "2.928", "-75.282");

        Page<Barbershop> page = barbershops.searchVisible(
                new Search(city.toUpperCase(), new BigDecimal("2.9273"), new BigDecimal("-75.2819")), FIRST);

        assertEquals(2, page.total());
        assertEquals(near.id(), page.items().get(0).id());
        assertEquals(far.id(), page.items().get(1).id());
    }

    @Test
    void services_are_scoped_by_barbershop_and_stored_with_their_key() {
        Barbershop shop = insertBarbershop("Neiva", null, null);
        Barbershop other = insertBarbershop("Neiva", null, null);
        Service cut = Service.create(UUID.randomUUID(), shop.id(), "Cut", null, 30, 2_500_000, NOW);
        Idempotency.Key key = key();

        services.saveNew(cut, key);

        assertEquals(cut.id(), services.findKey(key.key(), key.operation()).orElseThrow().resourceId());
        assertTrue(services.findById(other.id(), cut.id()).isEmpty());
        assertEquals(1, services.page(shop.id(), true, FIRST).total());
        services.update(cut.edit("Cut", null, 30, 2_500_000, java.util.Optional.of(false)));
        assertEquals(0, services.page(shop.id(), true, FIRST).total());
    }

    @Test
    void profiles_come_with_their_specialties_and_one_per_user() {
        Barbershop shop = insertBarbershop("Neiva", null, null);
        BarberProfile p = BarberProfile.create(UUID.randomUUID(), shop.id(), UUID.randomUUID(), 3, "Fades");
        barbers.saveNew(p, key());
        barbers.saveNewSpecialty(BarberSpecialty.create(UUID.randomUUID(), p.id(), "Fade"), key());

        assertEquals(1, barbers.findById(shop.id(), p.id()).orElseThrow().specialties().size());
        assertEquals(1, barbers.page(shop.id(), "Fade", FIRST).total());
        assertEquals(0, barbers.page(shop.id(), "Beard", FIRST).total());
        assertThrows(UserAlreadyHasProfile.class, () -> barbers.saveNew(
                BarberProfile.create(UUID.randomUUID(), shop.id(), p.userId(), 0, null), key()));
    }
}
