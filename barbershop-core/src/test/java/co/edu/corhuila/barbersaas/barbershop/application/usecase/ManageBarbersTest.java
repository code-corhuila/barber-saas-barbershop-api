package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.Forbidden;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarberUseCases.NewProfile;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Created;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Users.Unavailable;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Users.User;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManageBarbersTest {

    private static final Page.Request FIRST = new Page.Request(1, 20);

    private final Fakes.Barbers barbers = new Fakes.Barbers();
    private final Fakes.UsersDirectory users = new Fakes.UsersDirectory();
    private final ManageBarbers useCases = new ManageBarbers(barbers, users, UUID::randomUUID);

    private final UUID shop = UUID.randomUUID();
    private final UUID barberUser = users.add(new User(UUID.randomUUID(), "Juan Pérez", "https://cdn.example/juan.jpg",
            "BARBER", shop, true)).id();
    private final Caller owner = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, shop);
    private final Caller barber = new Caller(barberUser.toString(), Role.BARBER, shop);
    private final Caller otherBarber = new Caller(UUID.randomUUID().toString(), Role.BARBER, shop);
    private final Caller stranger = new Caller(UUID.randomUUID().toString(), Role.ADMIN_BARBERSHOP, UUID.randomUUID());

    private BarberProfile profile() {
        return useCases.create(owner, new NewProfile(barberUser, 3, "Fades"), "key-00000001").value();
    }

    @Test
    void the_owner_creates_one_profile_per_user_and_a_retry_returns_it() {
        BarberProfile p = profile();

        Created<BarberProfile> retry = useCases.create(owner, new NewProfile(barberUser, 3, "Fades"), "key-00000001");

        assertFalse(retry.created());
        assertEquals(p.id(), retry.value().id());
        assertThrows(BusinessRuleViolation.class,
                () -> useCases.create(owner, new NewProfile(barberUser, 0, null), "key-00000002"));
        assertThrows(Forbidden.class,
                () -> useCases.create(barber, new NewProfile(UUID.randomUUID(), 0, null), "key-00000003"));
    }

    @Test
    void a_new_profile_keeps_the_name_and_photo_identity_auth_answered() {
        BarberProfile p = profile();

        assertEquals("Juan Pérez", p.fullName());
        assertEquals("https://cdn.example/juan.jpg", barbers.rows.get(p.id()).profilePhotoUrl());
        assertEquals("Juan Pérez", useCases.list(owner, null, FIRST).items().get(0).fullName());
    }

    @Test
    void an_unknown_user_or_a_user_of_another_barbershop_is_not_found() {
        UUID elsewhere = users.add(new User(UUID.randomUUID(), "Ana", null, "BARBER", UUID.randomUUID(), true)).id();

        assertThrows(NotFound.class, () -> useCases.create(owner, new NewProfile(UUID.randomUUID(), 0, null),
                "key-00000030"));
        assertThrows(NotFound.class, () -> useCases.create(owner, new NewProfile(elsewhere, 0, null), "key-00000031"));
        assertTrue(barbers.rows.isEmpty());
    }

    @Test
    void only_an_active_barber_can_get_a_profile() {
        UUID client = users.add(new User(UUID.randomUUID(), "Luis", null, "CLIENT", shop, true)).id();
        UUID inactive = users.add(new User(UUID.randomUUID(), "Eva", null, "BARBER", shop, false)).id();

        assertThrows(BusinessRuleViolation.class,
                () -> useCases.create(owner, new NewProfile(client, 0, null), "key-00000040"));
        assertThrows(BusinessRuleViolation.class,
                () -> useCases.create(owner, new NewProfile(inactive, 0, null), "key-00000041"));
        assertTrue(barbers.rows.isEmpty());
    }

    @Test
    void without_identity_auth_no_profile_is_created_but_a_retry_still_returns_the_first() {
        BarberProfile p = profile();
        users.down = true;

        assertThrows(Unavailable.class,
                () -> useCases.create(owner, new NewProfile(UUID.randomUUID(), 0, null), "key-00000050"));
        assertEquals(p.id(), useCases.create(owner, new NewProfile(barberUser, 3, "Fades"), "key-00000001").value().id());
        assertEquals(1, barbers.rows.size());
    }

    @Test
    void a_barber_edits_only_their_own_profile() {
        BarberProfile p = profile();

        assertEquals(5, useCases.edit(barber, p.id(), Optional.of(5), null).experienceYears());
        assertThrows(Forbidden.class, () -> useCases.edit(otherBarber, p.id(), Optional.of(9), null));
        assertEquals("Fades", useCases.edit(owner, p.id(), null, null).bio());
    }

    @Test
    void specialties_are_added_and_removed_by_the_owner_or_the_barber_themselves() {
        BarberProfile p = profile();

        BarberSpecialty fade = useCases.addSpecialty(barber, p.id(), "Fade", "key-00000010").value();
        useCases.addSpecialty(owner, p.id(), "Beard", "key-00000011");

        assertEquals(2, useCases.specialties(owner, p.id(), FIRST).total());
        assertEquals(1, useCases.list(owner, "Fade", FIRST).total());
        assertThrows(Forbidden.class, () -> useCases.removeSpecialty(otherBarber, p.id(), fade.id()));
        useCases.removeSpecialty(barber, p.id(), fade.id());
        assertEquals(1, barbers.allSpecialties().size());
        assertThrows(NotFound.class, () -> useCases.removeSpecialty(owner, p.id(), fade.id()));
    }

    @Test
    void a_profile_of_another_barbershop_is_not_found() {
        BarberProfile p = profile();

        assertThrows(NotFound.class, () -> useCases.get(stranger, p.id()));
        assertThrows(NotFound.class, () -> useCases.edit(stranger, p.id(), Optional.of(1), null));
        assertThrows(NotFound.class, () -> useCases.addSpecialty(stranger, p.id(), "Fade", "key-00000020"));
        assertTrue(useCases.list(stranger, null, FIRST).items().isEmpty());
    }
}
