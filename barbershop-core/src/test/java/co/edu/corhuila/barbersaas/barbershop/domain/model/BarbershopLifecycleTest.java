package co.edu.corhuila.barbersaas.barbershop.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.InvalidStatusTransition;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** The lifecycle of entities-and-rules.md, "Entity: Barbershop" (INV-SHOP-002, DEC-SHOP-06). */
class BarbershopLifecycleTest {

    private static final Instant CREATED = Instant.parse("2026-10-02T15:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-06T15:00:00Z");

    private static Barbershop in(BarbershopStatus status) {
        Barbershop trial = Barbershop.register(UUID.randomUUID(), "El Clásico", "Neiva", CREATED);
        return new Barbershop(trial.id(), trial.name(), null, trial.city(), null, null, null, null, null, status, null,
                trial.timezone(), trial.cancellationPolicyHours(), trial.trialEndsAt(), CREATED, CREATED);
    }

    @ParameterizedTest
    @CsvSource({"TRIAL,ACTIVE", "TRIAL,SUSPENDED", "ACTIVE,SUSPENDED", "ACTIVE,CANCELLED", "SUSPENDED,ACTIVE",
            "SUSPENDED,CANCELLED"})
    void an_allowed_transition_changes_the_status_and_the_moment(BarbershopStatus from, BarbershopStatus to) {
        Barbershop changed = in(from).changeStatus(to, LATER);

        assertEquals(to, changed.status());
        assertEquals(LATER, changed.updatedAt());
    }

    @ParameterizedTest
    @CsvSource({"TRIAL,CANCELLED", "ACTIVE,TRIAL", "SUSPENDED,TRIAL", "CANCELLED,ACTIVE", "CANCELLED,SUSPENDED",
            "CANCELLED,TRIAL"})
    void any_other_transition_is_refused(BarbershopStatus from, BarbershopStatus to) {
        assertThrows(InvalidStatusTransition.class, () -> in(from).changeStatus(to, LATER));
    }

    @ParameterizedTest
    @EnumSource(BarbershopStatus.class)
    void setting_the_status_it_already_has_changes_nothing(BarbershopStatus status) {
        Barbershop shop = in(status);

        assertSame(shop, shop.changeStatus(status, LATER));
    }

    @ParameterizedTest
    @EnumSource(value = BarbershopStatus.class, names = {"TRIAL", "ACTIVE", "SUSPENDED"})
    void a_barbershop_that_is_not_cancelled_takes_a_plan(BarbershopStatus status) {
        UUID plan = UUID.randomUUID();

        Barbershop changed = in(status).assignPlan(plan, LATER);

        assertEquals(plan, changed.planId());
        assertEquals(status, changed.status());
        assertEquals(LATER, changed.updatedAt());
    }

    @Test
    void a_cancelled_barbershop_takes_no_plan() {
        assertThrows(InvalidStatusTransition.class,
                () -> in(BarbershopStatus.CANCELLED).assignPlan(UUID.randomUUID(), LATER));
    }

    @Test
    void the_same_plan_again_changes_nothing() {
        UUID plan = UUID.randomUUID();
        Barbershop shop = in(BarbershopStatus.ACTIVE).assignPlan(plan, LATER);

        assertSame(shop, shop.assignPlan(plan, LATER.plusSeconds(60)));
    }
}
