package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.PlatformBarbershopUseCases.Filter;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository.UserAlreadyHasProfile;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private final JdbcBarbershopRepository barbershops = new JdbcBarbershopRepository(jdbc, tx);
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
    void the_platform_list_sees_every_status_and_filters_by_plan_status_and_trial_end() {
        UUID plan = UUID.randomUUID();
        Barbershop older = Barbershop.register(UUID.randomUUID(), "Older", "Neiva", NOW.minus(70, ChronoUnit.DAYS));
        Barbershop newer = Barbershop.register(UUID.randomUUID(), "Newer", "Neiva", NOW);
        barbershops.saveNew(older, key());
        barbershops.saveNew(newer, key());
        assertTrue(barbershops.updateLifecycle(older.assignPlan(plan, NOW), BarbershopStatus.TRIAL));
        assertTrue(barbershops.updateLifecycle(newer.assignPlan(plan, NOW).changeStatus(BarbershopStatus.SUSPENDED, NOW),
                BarbershopStatus.TRIAL));

        Page<Barbershop> onPlan = barbershops.searchAll(new Filter(null, plan, null), FIRST);
        Page<Barbershop> expired = barbershops.searchAll(new Filter(BarbershopStatus.TRIAL, plan, NOW), FIRST);

        assertEquals(List.of(newer.id(), older.id()), onPlan.items().stream().map(Barbershop::id).toList());
        assertEquals(BarbershopStatus.SUSPENDED, onPlan.items().get(0).status());
        assertEquals(plan, onPlan.items().get(0).planId());
        assertEquals(List.of(older.id()), expired.items().stream().map(Barbershop::id).toList());
    }

    @Test
    void the_lifecycle_write_only_applies_while_the_status_is_the_one_read() {
        Barbershop shop = Barbershop.register(UUID.randomUUID(), "Shop", "Neiva", NOW);
        barbershops.saveNew(shop, key());

        assertFalse(barbershops.updateLifecycle(shop.changeStatus(BarbershopStatus.ACTIVE, NOW), BarbershopStatus.ACTIVE));
        assertEquals(BarbershopStatus.TRIAL, barbershops.findById(shop.id()).orElseThrow().status());
        assertTrue(barbershops.updateLifecycle(shop.changeStatus(BarbershopStatus.ACTIVE, NOW), BarbershopStatus.TRIAL));
        assertEquals(BarbershopStatus.ACTIVE, barbershops.findById(shop.id()).orElseThrow().status());
    }

    @Test
    void an_onboarded_barbershop_is_stored_with_its_key_and_removed_only_without_barbers() {
        Barbershop shop = Barbershop.register(UUID.randomUUID(), "Shop", "Calle 1", "Neiva", new BigDecimal("2.9273"),
                new BigDecimal("-75.2819"), null, NOW);
        Barbershop staffed = Barbershop.register(UUID.randomUUID(), "Staffed", "Neiva", NOW);
        Idempotency.Key key = key();

        barbershops.saveNew(shop, key);
        barbershops.saveNew(staffed, key());
        barbers.saveNew(BarberProfile.create(UUID.randomUUID(), staffed.id(), UUID.randomUUID(), null, null, 1, null), key());

        assertEquals(shop.id(), barbershops.findKey(key.key(), key.operation()).orElseThrow().resourceId());
        assertEquals(shop.trialEndsAt(), barbershops.findById(shop.id()).orElseThrow().trialEndsAt());
        assertTrue(barbershops.deleteIfRemovable(shop.id()));
        assertTrue(barbershops.findById(shop.id()).isEmpty());
        assertFalse(barbershops.deleteIfRemovable(shop.id()));
        assertFalse(barbershops.deleteIfRemovable(staffed.id()));
        assertTrue(barbershops.findById(staffed.id()).isPresent());
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
        BarberProfile p = BarberProfile.create(UUID.randomUUID(), shop.id(), UUID.randomUUID(), "Juan Perez",
                "https://cdn.example/juan.jpg", 3, "Fades");
        barbers.saveNew(p, key());
        barbers.saveNewSpecialty(BarberSpecialty.create(UUID.randomUUID(), p.id(), "Fade"), key());
        barbers.update(p.edit(java.util.Optional.of(4), null));

        BarberProfile stored = barbers.findById(shop.id(), p.id()).orElseThrow();
        assertEquals(1, stored.specialties().size());
        assertEquals("Juan Perez", stored.fullName());
        assertEquals("https://cdn.example/juan.jpg", barbers.page(shop.id(), null, FIRST).items().get(0).profilePhotoUrl());
        assertEquals(1, barbers.page(shop.id(), "Fade", FIRST).total());
        assertEquals(0, barbers.page(shop.id(), "Beard", FIRST).total());
        assertThrows(UserAlreadyHasProfile.class, () -> barbers.saveNew(
                BarberProfile.create(UUID.randomUUID(), shop.id(), p.userId(), null, null, 0, null), key()));
    }
}
