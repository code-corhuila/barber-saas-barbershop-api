package co.edu.corhuila.barbersaas.barbershop.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * platform-admin's internal plan read (platform-admin-service.yaml getPlanInternal, DEC-SHOP-07): only
 * what this domain needs to apply the barber limit. Plans are platform-admin's data, never copied here.
 */
public interface Plans {

    /** {@code maxBarbers} 999 means unlimited. */
    record Plan(UUID id, String name, int maxBarbers) { }

    /** Empty when platform-admin answers 404. */
    Optional<Plan> find(UUID id);

    /** platform-admin did not answer, answered something unexpected, or is not configured: 503. */
    class Unavailable extends RuntimeException {
        public Unavailable(String reason) {
            super(reason);
        }
    }
}
