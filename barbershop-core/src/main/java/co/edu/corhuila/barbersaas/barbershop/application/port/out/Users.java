package co.edu.corhuila.barbersaas.barbershop.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * identity-auth's internal user read (auth-service.yaml getInternalUser, DEC-AUTH-07): what this
 * domain may know of a user to check it and copy its name and photo (ADR-014). Never e-mail or phone.
 */
public interface Users {

    record User(UUID id, String fullName, String profilePhotoUrl, String role, UUID barbershopId, boolean active) { }

    /** Empty when identity-auth answers 404. */
    Optional<User> find(UUID id);

    /** identity-auth did not answer, answered something unexpected, or is not configured: 503. */
    class Unavailable extends RuntimeException {
        public Unavailable(String reason) {
            super(reason);
        }
    }
}
