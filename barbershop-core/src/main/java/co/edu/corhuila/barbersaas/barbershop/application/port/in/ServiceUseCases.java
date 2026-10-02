package co.edu.corhuila.barbersaas.barbershop.application.port.in;

import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.util.Optional;
import java.util.UUID;

/** The service catalog of the token's barbershop (barbershop-service.yaml, tag Services). */
public interface ServiceUseCases {

    record ServiceData(String name, String description, int durationMinutes, long priceCents) { }

    /** ADMIN_BARBERSHOP sees inactive ones too; BARBER and CLIENT only active ones, whatever they ask. */
    Page<Service> list(Caller caller, Boolean active, Page.Request page);

    /** 404 for another barbershop's service, and for an inactive one when the caller is not the owner. */
    Service get(Caller caller, UUID id);

    /** ADMIN_BARBERSHOP; bound to the token's barbershop and created active (FR-005). */
    Created<Service> create(Caller caller, ServiceData data, String idempotencyKey);

    /** ADMIN_BARBERSHOP; {@code active} empty keeps the current activation. */
    Service update(Caller caller, UUID id, ServiceData data, Optional<Boolean> active);
}
