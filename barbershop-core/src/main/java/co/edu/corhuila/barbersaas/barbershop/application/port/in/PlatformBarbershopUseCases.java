package co.edu.corhuila.barbersaas.barbershop.application.port.in;

import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * What platform-admin does with any barbershop (barbershop-service.yaml 1.4.0, tag Internal,
 * DEC-SHOP-06). platform-admin never touches this schema: it calls these operations with its own
 * service token. Plans are its data, so it checks the plan exists and is active; this service keeps
 * the row and the lifecycle.
 */
public interface PlatformBarbershopUseCases {

    /** The {@code sub} of the only service token these operations accept. */
    String PLATFORM_ADMIN = "barber-saas-platform-admin-api";

    /** Every filter is optional; trialEndsBefore with status TRIAL finds the expired trials (FR-026). */
    record Filter(BarbershopStatus status, UUID planId, Instant trialEndsBefore) { }

    /** Every barbershop in any status, most recent first. */
    Page<Barbershop> list(Caller caller, Filter filter, Page.Request page);

    Barbershop get(Caller caller, UUID id);

    /** The lifecycle of the domain; the status it already has changes nothing. */
    Barbershop changeStatus(Caller caller, UUID id, BarbershopStatus target);

    Barbershop assignPlan(Caller caller, UUID id, UUID planId);
}
