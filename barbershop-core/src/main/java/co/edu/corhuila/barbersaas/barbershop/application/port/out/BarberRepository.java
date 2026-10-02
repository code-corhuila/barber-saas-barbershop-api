package co.edu.corhuila.barbersaas.barbershop.application.port.out;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import java.util.Optional;
import java.util.UUID;

/** Persistence of barbershop.barber_profile and barbershop.barber_specialty. Profiles come with their specialties. */
public interface BarberRepository {

    /** {@code specialty} null: every profile; otherwise those with that exact specialty name. */
    Page<BarberProfile> page(UUID barbershopId, String specialty, Page.Request page);

    Optional<BarberProfile> findById(UUID barbershopId, UUID id);

    boolean existsForUser(UUID userId);

    Optional<Idempotency.Stored> findKey(String key, String operation);

    /** Writes the profile and its idempotency key in ONE transaction; UserAlreadyHasProfile on uq_barber_profile_user. */
    void saveNew(BarberProfile profile, Idempotency.Key key);

    void update(BarberProfile profile);

    Page<BarberSpecialty> specialties(UUID barberProfileId, Page.Request page);

    Optional<BarberSpecialty> findSpecialty(UUID barberProfileId, UUID specialtyId);

    /** Writes the specialty and its idempotency key in ONE transaction. */
    void saveNewSpecialty(BarberSpecialty specialty, Idempotency.Key key);

    void deleteSpecialty(UUID specialtyId);

    /** Raised when the unique user index rejects a concurrent creation. */
    class UserAlreadyHasProfile extends RuntimeException {
        public UserAlreadyHasProfile() {
            super("The user already has a barber profile");
        }
    }
}
