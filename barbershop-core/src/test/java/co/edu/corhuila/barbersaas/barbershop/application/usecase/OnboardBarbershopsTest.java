package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Created;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.InternalBarbershopUseCases.NewBarbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OnboardBarbershopsTest {

    private static final Instant NOW = Instant.parse("2026-10-04T15:00:00Z");
    private static final Caller WORKFLOW = new Caller("barber-saas-workflow", Role.SERVICE, null);
    private static final NewBarbershop DATA = new NewBarbershop("El Clásico", "Calle 5 # 10-20", "Neiva",
            new BigDecimal("2.9273"), new BigDecimal("-75.2819"), "+573001112233");

    private final Fakes.Barbershops barbershops = new Fakes.Barbershops();
    private final Fakes.Barbers barbers = new Fakes.Barbers();
    private final OnboardBarbershops useCases = new OnboardBarbershops(barbershops, barbers, UUID::randomUUID,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void the_workflow_creates_a_barbershop_on_trial_for_sixty_days() {
        Created<Barbershop> result = useCases.create(WORKFLOW, DATA, "signup-0001");

        assertTrue(result.created());
        Barbershop stored = barbershops.rows.get(result.value().id());
        assertEquals(BarbershopStatus.TRIAL, stored.status());
        assertEquals(NOW.plus(Duration.ofDays(60)), stored.trialEndsAt());
        assertEquals("Calle 5 # 10-20", stored.address());
    }

    @Test
    void a_retry_with_the_same_key_returns_the_same_barbershop() {
        Barbershop first = useCases.create(WORKFLOW, DATA, "signup-0002").value();

        Created<Barbershop> retry = useCases.create(WORKFLOW, DATA, "signup-0002");

        assertFalse(retry.created());
        assertEquals(first.id(), retry.value().id());
        assertEquals(1, barbershops.rows.size());
    }

    @Test
    void the_same_key_with_other_data_is_refused() {
        useCases.create(WORKFLOW, DATA, "signup-0003");

        assertThrows(IdempotencyKeyReused.class, () -> useCases.create(WORKFLOW,
                new NewBarbershop("Otra", null, "Neiva", null, null, null), "signup-0003"));
    }

    @Test
    void platform_admin_also_creates_a_barbershop_on_trial_but_never_removes_one() {
        Caller platform = new Caller("barber-saas-platform-admin-api", Role.SERVICE, null);

        Created<Barbershop> result = useCases.create(platform, DATA, "platform-0001");

        assertTrue(result.created());
        assertEquals(BarbershopStatus.TRIAL, result.value().status());
        assertThrows(Forbidden.class, () -> useCases.remove(platform, result.value().id()));
    }

    @Test
    void only_the_workflow_service_token_may_call_the_internal_operations() {
        UUID shop = UUID.randomUUID();
        Caller owner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, shop);
        Caller superAdmin = new Caller(UUID.randomUUID().toString(), Role.SUPER_ADMIN, null);
        Caller worker = new Caller("barber-saas-worker", Role.SERVICE, null);

        for (Caller caller : new Caller[] {owner, superAdmin, worker}) {
            assertThrows(Forbidden.class, () -> useCases.create(caller, DATA, "signup-0004"));
            assertThrows(Forbidden.class, () -> useCases.remove(caller, shop));
        }
        assertTrue(barbershops.rows.isEmpty());
    }

    @Test
    void the_compensation_removes_a_trial_barbershop_and_a_second_one_still_succeeds() {
        Barbershop shop = useCases.create(WORKFLOW, DATA, "signup-0005").value();

        useCases.remove(WORKFLOW, shop.id());
        useCases.remove(WORKFLOW, shop.id());

        assertTrue(barbershops.rows.isEmpty());
    }

    @Test
    void a_barbershop_with_barbers_is_not_removed() {
        Barbershop shop = useCases.create(WORKFLOW, DATA, "signup-0006").value();
        barbers.add(BarberProfile.create(UUID.randomUUID(), shop.id(), UUID.randomUUID(), null, null, 1, null));

        assertThrows(BusinessRuleViolation.class, () -> useCases.remove(WORKFLOW, shop.id()));
        assertTrue(barbershops.rows.containsKey(shop.id()));
    }
}
