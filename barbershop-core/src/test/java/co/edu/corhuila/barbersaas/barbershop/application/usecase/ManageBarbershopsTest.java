package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopChanges;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageBarbershopsTest {

    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");
    private static final Page.Request FIRST = new Page.Request(1, 20);

    private final Fakes.Barbershops barbershops = new Fakes.Barbershops();
    private final Fakes.Services services = new Fakes.Services();
    private final Fakes.Barbers barbers = new Fakes.Barbers();
    private final ManageBarbershops useCases = new ManageBarbershops(barbershops, services, barbers,
            Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC));

    private final Barbershop shop = barbershops.add(Barbershop.register(UUID.randomUUID(), "El Clásico", "Neiva", NOW));
    private final Barbershop other = barbershops.add(Barbershop.register(UUID.randomUUID(), "La Otra", "Neiva", NOW));

    private static Caller staff(Role role, Barbershop b) {
        return new Caller(UUID.randomUUID().toString(), role, b.id());
    }

    private Barbershop suspended() {
        Barbershop b = Barbershop.register(UUID.randomUUID(), "Cerrada", "Neiva", NOW);
        return barbershops.add(new Barbershop(b.id(), b.name(), null, b.city(), null, null, null, null, null,
                BarbershopStatus.SUSPENDED, null, b.timezone(), 2, b.trialEndsAt(), NOW, NOW));
    }

    @Test
    void the_public_search_hides_suspended_barbershops() {
        suspended();

        Page<Barbershop> page = useCases.search(new Search("neiva", null, null), FIRST);

        assertEquals(2, page.total());
    }

    @Test
    void a_suspended_or_unknown_barbershop_is_not_found_in_the_public_catalog() {
        Barbershop closed = suspended();

        assertThrows(NotFound.class, () -> useCases.getVisible(closed.id()));
        assertThrows(NotFound.class, () -> useCases.getVisible(UUID.randomUUID()));
        assertThrows(NotFound.class, () -> useCases.listVisibleServices(closed.id(), FIRST));
    }

    @Test
    void the_public_catalog_lists_only_active_services_of_that_barbershop() {
        services.add(Service.create(UUID.randomUUID(), shop.id(), "Cut", null, 30, 1000, NOW));
        services.add(Service.create(UUID.randomUUID(), shop.id(), "Old", null, 30, 1000, NOW)
                .edit("Old", null, 30, 1000, Optional.of(false)));
        services.add(Service.create(UUID.randomUUID(), other.id(), "Elsewhere", null, 30, 1000, NOW));

        Page<Service> page = useCases.listVisibleServices(shop.id(), FIRST);

        assertEquals(1, page.total());
        assertEquals("Cut", page.items().get(0).name());
    }

    @Test
    void my_barbershop_is_always_the_one_of_the_token() {
        assertEquals(shop.id(), useCases.getMine(staff(Role.BARBER, shop)).id());
        assertEquals(other.id(), useCases.getMine(staff(Role.ADMIN_BARBERSHOP, other)).id());
    }

    @Test
    void a_caller_without_a_barbershop_cannot_read_or_edit_mine() {
        Caller superAdmin = new Caller(UUID.randomUUID().toString(), Role.SUPER_ADMIN, null);
        Caller client = new Caller(UUID.randomUUID().toString(), Role.CLIENT, null);

        assertThrows(Forbidden.class, () -> useCases.getMine(superAdmin));
        assertThrows(Forbidden.class, () -> useCases.getMine(client));
    }

    @Test
    void only_the_owner_edits_the_barbershop_and_the_edit_is_stored() {
        assertThrows(Forbidden.class, () -> useCases.editMine(staff(Role.BARBER, shop),
                BarbershopChanges.none().withCancellationPolicyHours(4)));

        Barbershop edited = useCases.editMine(staff(Role.ADMIN_BARBERSHOP, shop),
                BarbershopChanges.none().withCancellationPolicyHours(4));

        assertEquals(4, edited.cancellationPolicyHours());
        assertEquals(4, barbershops.rows.get(shop.id()).cancellationPolicyHours());
        assertEquals(NOW.plusSeconds(3600), barbershops.rows.get(shop.id()).updatedAt());
        assertEquals(2, barbershops.rows.get(other.id()).cancellationPolicyHours());
    }
}
