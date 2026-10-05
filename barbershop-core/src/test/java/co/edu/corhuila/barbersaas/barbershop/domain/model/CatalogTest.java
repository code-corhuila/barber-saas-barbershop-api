package co.edu.corhuila.barbersaas.barbershop.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The service catalog and the barbers: the rules appointment relies on before booking. */
class CatalogTest {

    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");
    private static final UUID SHOP = UUID.randomUUID();

    @Test
    void a_new_service_is_active_and_keeps_price_in_cents() {
        Service s = Service.create(UUID.randomUUID(), SHOP, "Classic haircut", null, 30, 2_500_000, NOW);

        assertTrue(s.active());
        assertEquals(2_500_000, s.priceCents());
        assertEquals(30, s.durationMinutes());
    }

    @Test
    void a_service_lasts_at_least_five_minutes_and_never_costs_less_than_zero() {
        assertThrows(BusinessRuleViolation.class,
                () -> Service.create(UUID.randomUUID(), SHOP, "Quick", null, 4, 0, NOW));
        assertThrows(BusinessRuleViolation.class,
                () -> Service.create(UUID.randomUUID(), SHOP, "Free?", null, 30, -1, NOW));
        assertThrows(BusinessRuleViolation.class,
                () -> Service.create(UUID.randomUUID(), SHOP, "", null, 30, 0, NOW));
        assertThrows(BusinessRuleViolation.class,
                () -> Service.create(UUID.randomUUID(), SHOP, "Cut", "d".repeat(256), 30, 0, NOW));
    }

    @Test
    void editing_a_service_replaces_its_data_and_keeps_activation_when_omitted() {
        Service s = Service.create(UUID.randomUUID(), SHOP, "Cut", null, 30, 1000, NOW);

        Service deactivated = s.edit("Cut and beard", "With towel", 45, 1500, Optional.of(false));
        Service kept = deactivated.edit("Cut and beard", null, 45, 1500, Optional.empty());

        assertEquals("Cut and beard", deactivated.name());
        assertFalse(deactivated.active());
        assertFalse(kept.active());
        assertEquals(s.createdAt(), kept.createdAt());
        assertEquals(SHOP, kept.barbershopId());
    }

    @Test
    void a_barber_profile_starts_without_ratings_and_validates_its_bio() {
        BarberProfile p = BarberProfile.create(UUID.randomUUID(), SHOP, UUID.randomUUID(), null, null, 3, "Fades");

        assertEquals(BigDecimal.ZERO, p.ratingAvg());
        assertEquals(0, p.ratingCount());
        assertEquals(List.of(), p.specialties());
        assertThrows(BusinessRuleViolation.class,
                () -> BarberProfile.create(UUID.randomUUID(), SHOP, UUID.randomUUID(), null, null, -1, null));
        assertThrows(BusinessRuleViolation.class,
                () -> BarberProfile.create(UUID.randomUUID(), SHOP, UUID.randomUUID(), null, null, 0, "b".repeat(501)));
    }

    @Test
    void a_profile_keeps_the_name_and_photo_snapshot_and_an_edit_never_changes_it() {
        BarberProfile p = BarberProfile.create(UUID.randomUUID(), SHOP, UUID.randomUUID(), " Juan Pérez ",
                "https://cdn.example/juan.jpg", 3, null);

        BarberProfile edited = p.edit(Optional.of(5), Optional.of("Fades"));

        assertEquals("Juan Pérez", edited.fullName());
        assertEquals("https://cdn.example/juan.jpg", edited.profilePhotoUrl());
        assertEquals("Juan Pérez", edited.withSpecialties(List.of()).fullName());
        assertEquals(null, BarberProfile.create(UUID.randomUUID(), SHOP, UUID.randomUUID(), null, " ", 0, null)
                .profilePhotoUrl());
        assertThrows(BusinessRuleViolation.class, () -> BarberProfile.create(UUID.randomUUID(), SHOP,
                UUID.randomUUID(), "x".repeat(121), null, 0, null));
    }

    @Test
    void only_the_owner_of_a_profile_is_its_barber() {
        UUID user = UUID.randomUUID();
        BarberProfile p = BarberProfile.create(UUID.randomUUID(), SHOP, user, null, null, 0, null);

        assertTrue(p.belongsTo(user));
        assertFalse(p.belongsTo(UUID.randomUUID()));
    }

    @Test
    void editing_a_profile_changes_only_what_was_sent() {
        BarberProfile p = BarberProfile.create(UUID.randomUUID(), SHOP, UUID.randomUUID(), null, null, 3, "Fades");

        BarberProfile edited = p.edit(Optional.of(5), null);
        BarberProfile cleared = edited.edit(null, Optional.empty());

        assertEquals(5, edited.experienceYears());
        assertEquals("Fades", edited.bio());
        assertEquals(null, cleared.bio());
    }

    @Test
    void a_specialty_name_has_between_one_and_eighty_characters() {
        BarberSpecialty s = BarberSpecialty.create(UUID.randomUUID(), UUID.randomUUID(), "  Fade  ");

        assertEquals("Fade", s.specialtyName());
        assertThrows(BusinessRuleViolation.class,
                () -> BarberSpecialty.create(UUID.randomUUID(), UUID.randomUUID(), " "));
        assertThrows(BusinessRuleViolation.class,
                () -> BarberSpecialty.create(UUID.randomUUID(), UUID.randomUUID(), "x".repeat(81)));
    }
}
