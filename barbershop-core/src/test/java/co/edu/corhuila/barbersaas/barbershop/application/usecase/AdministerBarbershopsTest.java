package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.PlatformBarbershopUseCases.Filter;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.InvalidStatusTransition;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** DEC-SHOP-06: platform-admin lists, reads and changes any barbershop through internal operations. */
class AdministerBarbershopsTest {

    private static final Instant NOW = Instant.parse("2026-10-06T15:00:00Z");
    private static final Caller PLATFORM = new Caller("barber-saas-platform-admin-api", Role.SERVICE, null);
    private static final Filter ANY = new Filter(null, null, null);
    private static final Page.Request FIRST = new Page.Request(1, 20);

    private final Fakes.Barbershops barbershops = new Fakes.Barbershops();
    private final AdministerBarbershops useCases = new AdministerBarbershops(barbershops,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private Barbershop shop(BarbershopStatus status, int daysAgo, UUID plan) {
        Instant created = NOW.minus(Duration.ofDays(daysAgo));
        Barbershop trial = Barbershop.register(UUID.randomUUID(), "Barbería " + daysAgo, "Neiva", created);
        return barbershops.add(new Barbershop(trial.id(), trial.name(), null, trial.city(), null, null, null, null,
                null, status, plan, trial.timezone(), trial.cancellationPolicyHours(), trial.trialEndsAt(), created,
                created));
    }

    @Test
    void lists_every_barbershop_in_any_status_most_recent_first() {
        Barbershop old = shop(BarbershopStatus.CANCELLED, 30, null);
        Barbershop recent = shop(BarbershopStatus.SUSPENDED, 1, null);

        Page<Barbershop> page = useCases.list(PLATFORM, ANY, FIRST);

        assertEquals(List.of(recent.id(), old.id()), page.items().stream().map(Barbershop::id).toList());
        assertEquals(2, page.total());
    }

    @Test
    void filters_by_status_plan_and_trial_end() {
        UUID plan = UUID.randomUUID();
        Barbershop onPlan = shop(BarbershopStatus.ACTIVE, 10, plan);
        shop(BarbershopStatus.ACTIVE, 9, null);
        Barbershop expired = shop(BarbershopStatus.TRIAL, 70, null);
        shop(BarbershopStatus.TRIAL, 5, null);

        assertEquals(List.of(onPlan.id()), ids(useCases.list(PLATFORM, new Filter(null, plan, null), FIRST)));
        assertEquals(List.of(expired.id()),
                ids(useCases.list(PLATFORM, new Filter(BarbershopStatus.TRIAL, null, NOW), FIRST)));
        assertEquals(2, useCases.list(PLATFORM, new Filter(BarbershopStatus.ACTIVE, null, null), FIRST).total());
    }

    @Test
    void reads_any_barbershop_and_an_unknown_one_is_not_found() {
        Barbershop cancelled = shop(BarbershopStatus.CANCELLED, 3, null);

        assertEquals(cancelled.id(), useCases.get(PLATFORM, cancelled.id()).id());
        assertThrows(NotFound.class, () -> useCases.get(PLATFORM, UUID.randomUUID()));
    }

    @Test
    void changes_the_status_and_stores_it() {
        Barbershop trial = shop(BarbershopStatus.TRIAL, 3, null);

        Barbershop active = useCases.changeStatus(PLATFORM, trial.id(), BarbershopStatus.ACTIVE);

        assertEquals(BarbershopStatus.ACTIVE, active.status());
        assertEquals(NOW, active.updatedAt());
        assertEquals(BarbershopStatus.ACTIVE, barbershops.rows.get(trial.id()).status());
    }

    @Test
    void the_same_status_again_changes_nothing() {
        Barbershop active = shop(BarbershopStatus.ACTIVE, 3, null);

        assertSame(active, useCases.changeStatus(PLATFORM, active.id(), BarbershopStatus.ACTIVE));
    }

    @Test
    void a_transition_the_lifecycle_forbids_is_refused_and_nothing_changes() {
        Barbershop cancelled = shop(BarbershopStatus.CANCELLED, 3, null);

        assertThrows(InvalidStatusTransition.class,
                () -> useCases.changeStatus(PLATFORM, cancelled.id(), BarbershopStatus.ACTIVE));
        assertSame(cancelled, barbershops.rows.get(cancelled.id()));
    }

    @Test
    void a_status_changed_meanwhile_is_refused_instead_of_overwritten() {
        Barbershop trial = shop(BarbershopStatus.TRIAL, 3, null);
        barbershops.changedBehindOurBack = true;

        assertThrows(InvalidStatusTransition.class,
                () -> useCases.changeStatus(PLATFORM, trial.id(), BarbershopStatus.ACTIVE));
    }

    @Test
    void assigns_the_plan_and_a_cancelled_barbershop_takes_none() {
        UUID plan = UUID.randomUUID();
        Barbershop active = shop(BarbershopStatus.ACTIVE, 3, null);
        Barbershop cancelled = shop(BarbershopStatus.CANCELLED, 3, null);

        assertEquals(plan, useCases.assignPlan(PLATFORM, active.id(), plan).planId());
        assertEquals(plan, barbershops.rows.get(active.id()).planId());
        assertThrows(InvalidStatusTransition.class, () -> useCases.assignPlan(PLATFORM, cancelled.id(), plan));
        assertThrows(NotFound.class, () -> useCases.assignPlan(PLATFORM, UUID.randomUUID(), plan));
    }

    @Test
    void only_the_platform_admin_service_token_may_call_them() {
        Barbershop trial = shop(BarbershopStatus.TRIAL, 3, null);
        Caller superAdmin = new Caller(UUID.randomUUID().toString(), Role.SUPER_ADMIN, null);
        Caller workflow = new Caller("barber-saas-workflow", Role.SERVICE, null);

        for (Caller caller : new Caller[] {superAdmin, workflow}) {
            assertThrows(Forbidden.class, () -> useCases.list(caller, ANY, FIRST));
            assertThrows(Forbidden.class, () -> useCases.get(caller, trial.id()));
            assertThrows(Forbidden.class, () -> useCases.changeStatus(caller, trial.id(), BarbershopStatus.ACTIVE));
            assertThrows(Forbidden.class, () -> useCases.assignPlan(caller, trial.id(), UUID.randomUUID()));
        }
        assertSame(trial, barbershops.rows.get(trial.id()));
    }

    private static List<UUID> ids(Page<Barbershop> page) {
        return page.items().stream().map(Barbershop::id).toList();
    }
}
