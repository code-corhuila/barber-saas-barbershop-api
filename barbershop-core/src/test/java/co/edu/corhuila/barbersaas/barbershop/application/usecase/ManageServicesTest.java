package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.IdempotencyKeyReused;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Created;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ServiceUseCases.ServiceData;
import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageServicesTest {

    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");
    private static final Page.Request FIRST = new Page.Request(1, 20);
    private static final ServiceData CUT = new ServiceData("Classic haircut", null, 30, 2_500_000);

    private final Fakes.Services services = new Fakes.Services();
    private final ManageServices useCases = new ManageServices(services, UUID::randomUUID,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID shop = UUID.randomUUID();
    private final Caller owner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, shop);
    private final Caller barber = new Caller(UUID.randomUUID().toString(), Role.BARBER, shop);
    private final Caller otherOwner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, UUID.randomUUID());

    @Test
    void the_owner_creates_an_active_service_in_the_token_barbershop() {
        Created<Service> result = useCases.create(owner, CUT, "key-00000001");

        assertTrue(result.created());
        assertTrue(result.value().active());
        assertEquals(shop, result.value().barbershopId());
        assertEquals(NOW, result.value().createdAt());
    }

    @Test
    void a_retry_with_the_same_key_returns_the_first_service_and_creates_nothing() {
        Service first = useCases.create(owner, CUT, "key-00000001").value();

        Created<Service> retry = useCases.create(owner, CUT, "key-00000001");

        assertFalse(retry.created());
        assertEquals(first.id(), retry.value().id());
        assertEquals(1, services.rows.size());
    }

    @Test
    void the_same_key_with_another_body_or_from_another_barbershop_is_refused() {
        useCases.create(owner, CUT, "key-00000001");

        assertThrows(IdempotencyKeyReused.class,
                () -> useCases.create(owner, new ServiceData("Beard", null, 20, 1000), "key-00000001"));
        assertThrows(IdempotencyKeyReused.class, () -> useCases.create(otherOwner, CUT, "key-00000001"));
    }

    @Test
    void only_the_owner_creates_or_edits_and_the_domain_rules_apply() {
        assertThrows(Forbidden.class, () -> useCases.create(barber, CUT, "key-00000001"));
        assertThrows(BusinessRuleViolation.class,
                () -> useCases.create(owner, new ServiceData("Quick", null, 4, 0), "key-00000002"));
    }

    @Test
    void staff_and_clients_see_only_active_services() {
        Service s = useCases.create(owner, CUT, "key-00000001").value();
        useCases.update(owner, s.id(), CUT, Optional.of(false));

        assertEquals(1, useCases.list(owner, null, FIRST).total());
        assertEquals(0, useCases.list(barber, false, FIRST).total());
        assertThrows(NotFound.class, () -> useCases.get(barber, s.id()));
        assertFalse(useCases.get(owner, s.id()).active());
    }

    @Test
    void a_service_of_another_barbershop_is_not_found() {
        Service s = useCases.create(owner, CUT, "key-00000001").value();

        assertThrows(NotFound.class, () -> useCases.get(otherOwner, s.id()));
        assertThrows(NotFound.class, () -> useCases.update(otherOwner, s.id(), CUT, Optional.empty()));
        assertEquals(0, useCases.list(otherOwner, null, FIRST).total());
    }
}
