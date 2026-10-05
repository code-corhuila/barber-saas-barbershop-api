package co.edu.corhuila.barbersaas.barbershop.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BarbershopTest {

    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");

    private static Barbershop registered() {
        return Barbershop.register(UUID.randomUUID(), "El Clásico", "Neiva", NOW);
    }

    @Test
    void a_new_barbershop_starts_on_trial_for_sixty_days() {
        Barbershop shop = registered();

        assertEquals(BarbershopStatus.TRIAL, shop.status());
        assertEquals(NOW.plus(Duration.ofDays(60)), shop.trialEndsAt());
        assertEquals("America/Bogota", shop.timezone());
        assertEquals(2, shop.cancellationPolicyHours());
    }

    @Test
    void a_barbershop_registered_with_its_optional_data_also_starts_on_trial() {
        Barbershop shop = Barbershop.register(UUID.randomUUID(), "El Clásico", " Calle 5 # 10-20 ", "Neiva",
                new BigDecimal("2.9273"), new BigDecimal("-75.2819"), "", NOW);

        assertEquals(BarbershopStatus.TRIAL, shop.status());
        assertEquals(NOW.plus(Duration.ofDays(60)), shop.trialEndsAt());
        assertEquals(NOW, shop.createdAt());
        assertEquals("Calle 5 # 10-20", shop.address());
        assertNull(shop.phone());
        assertNull(shop.planId());
    }

    @Test
    void only_a_trial_barbershop_without_barbers_can_be_removed() {
        Barbershop trial = registered();
        Barbershop active = new Barbershop(trial.id(), trial.name(), null, trial.city(), null, null, null, null, null,
                BarbershopStatus.ACTIVE, null, trial.timezone(), 2, trial.trialEndsAt(), NOW, NOW);

        trial.requireRemovable(false);
        assertThrows(BusinessRuleViolation.class, () -> trial.requireRemovable(true));
        assertThrows(BusinessRuleViolation.class, () -> active.requireRemovable(false));
    }

    @Test
    void only_active_and_trial_barbershops_are_visible_to_clients() {
        assertTrue(BarbershopStatus.TRIAL.visible());
        assertTrue(BarbershopStatus.ACTIVE.visible());
        assertFalse(BarbershopStatus.SUSPENDED.visible());
        assertFalse(BarbershopStatus.CANCELLED.visible());
    }

    @Test
    void the_name_and_the_city_are_required() {
        assertThrows(BusinessRuleViolation.class, () -> Barbershop.register(UUID.randomUUID(), " ", "Neiva", NOW));
        assertThrows(BusinessRuleViolation.class, () -> Barbershop.register(UUID.randomUUID(), "X", "", NOW));
        assertThrows(BusinessRuleViolation.class,
                () -> Barbershop.register(UUID.randomUUID(), "x".repeat(121), "Neiva", NOW));
    }

    @Test
    void an_edit_changes_only_the_fields_sent_and_can_clear_optional_ones() {
        Barbershop shop = registered().edit(BarbershopChanges.none()
                .withAddress(Optional.of("Calle 1 # 2-3"))
                .withPhone(Optional.of("+573001112233")), NOW);

        Barbershop edited = shop.edit(BarbershopChanges.none()
                .withAddress(Optional.empty())
                .withCancellationPolicyHours(4), NOW.plusSeconds(60));

        assertNull(edited.address());
        assertEquals("+573001112233", edited.phone());
        assertEquals(4, edited.cancellationPolicyHours());
        assertEquals("El Clásico", edited.name());
        assertEquals(NOW.plusSeconds(60), edited.updatedAt());
        assertEquals(shop.trialEndsAt(), edited.trialEndsAt());
    }

    @Test
    void an_edit_rejects_values_the_database_would_reject() {
        Barbershop shop = registered();

        assertThrows(BusinessRuleViolation.class,
                () -> shop.edit(BarbershopChanges.none().withCancellationPolicyHours(-1), NOW));
        assertThrows(BusinessRuleViolation.class,
                () -> shop.edit(BarbershopChanges.none().withLatitude(Optional.of(new BigDecimal("91"))), NOW));
        assertThrows(BusinessRuleViolation.class,
                () -> shop.edit(BarbershopChanges.none().withLongitude(Optional.of(new BigDecimal("-181"))), NOW));
        assertThrows(BusinessRuleViolation.class,
                () -> shop.edit(BarbershopChanges.none().withPhone(Optional.of("1".repeat(21))), NOW));
        assertThrows(BusinessRuleViolation.class,
                () -> shop.edit(BarbershopChanges.none().withTimezone("Mars/Olympus"), NOW));
    }
}
