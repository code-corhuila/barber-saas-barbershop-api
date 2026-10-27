package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarberUseCases.NewProfile;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Plans;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Users.User;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DEC-SHOP-07: POST /api/v1/barbers reads the plan from platform-admin before creating a profile. */
class BarberLimitOfThePlanTest {

    private static final Instant NOW = Instant.parse("2026-10-27T15:00:00Z");

    private final Fakes.Barbers barbers = new Fakes.Barbers();
    private final Fakes.Barbershops barbershops = new Fakes.Barbershops();
    private final Fakes.UsersDirectory users = new Fakes.UsersDirectory();
    private final Fakes.PlansCatalog plans = new Fakes.PlansCatalog();
    private final ManageBarbers useCases = new ManageBarbers(barbers, barbershops, users, plans, UUID::randomUUID);

    private final Plans.Plan basico = plans.add("Basico", 2);
    private final Plans.Plan premium = plans.add("Premium", 999);

    private Caller ownerOf(Plans.Plan plan, int barbersAlready) {
        Barbershop shop = Barbershop.register(UUID.randomUUID(), "El Clásico", "Neiva", NOW)
                .assignPlan(plan.id(), NOW).withActiveBarbers(barbersAlready);
        barbershops.add(shop);
        return new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, shop.id());
    }

    private NewProfile newBarberOf(Caller owner) {
        UUID user = users.add(new User(UUID.randomUUID(), "Juan", null, "BARBER", owner.tenant(), true)).id();
        return new NewProfile(user, 1, null);
    }

    @Test
    void within_the_limit_the_profile_is_created() {
        Caller owner = ownerOf(basico, 1);

        assertTrue(useCases.create(owner, newBarberOf(owner), "key-00000001").created());
        assertEquals(1, plans.calls);
    }

    @Test
    void at_the_limit_the_plan_refuses_and_nothing_is_created() {
        Caller owner = ownerOf(basico, 2);

        BusinessRuleViolation e = assertThrows(BusinessRuleViolation.class,
                () -> useCases.create(owner, newBarberOf(owner), "key-00000002"));

        assertEquals("Tu plan permite 2 barberos", e.getMessage());
        assertTrue(barbers.rows.isEmpty());
        assertEquals(0, users.calls);
    }

    @Test
    void platform_admin_down_creates_nothing() {
        Caller owner = ownerOf(basico, 0);
        plans.down = true;

        assertThrows(Plans.Unavailable.class, () -> useCases.create(owner, newBarberOf(owner), "key-00000003"));
        assertTrue(barbers.rows.isEmpty());
    }

    @Test
    void premium_never_limits() {
        Caller owner = ownerOf(premium, 998);

        assertTrue(useCases.create(owner, newBarberOf(owner), "key-00000004").created());
        assertTrue(useCases.create(ownerOf(premium, 2000), newBarberOf(owner), "key-00000005").created());
    }

    @Test
    void a_barbershop_without_a_plan_is_not_limited_and_platform_admin_is_not_asked() {
        Barbershop shop = barbershops.add(Barbershop.register(UUID.randomUUID(), "Sin plan", "Neiva", NOW)
                .withActiveBarbers(50));
        Caller owner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, shop.id());

        assertTrue(useCases.create(owner, newBarberOf(owner), "key-00000006").created());
        assertEquals(0, plans.calls);
    }

    @Test
    void a_retry_of_a_created_profile_returns_it_even_when_the_limit_is_now_reached() {
        Caller owner = ownerOf(basico, 1);
        NewProfile profile = newBarberOf(owner);
        useCases.create(owner, profile, "key-00000007");
        barbershops.add(barbershops.rows.get(owner.tenant()).withActiveBarbers(2));

        assertFalse(useCases.create(owner, profile, "key-00000007").created());
    }
}
