package co.edu.corhuila.barbersaas.barbershop.application.port.in;

import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The barbershop steps of the owner-onboarding saga (barbershop-service.yaml, tag Internal, DEC-SHOP-05).
 * Only barber-saas-workflow calls them, with its service token; no tenant comes from a token here.
 */
public interface InternalBarbershopUseCases {

    /** The {@code sub} of the only service token these operations accept. */
    String WORKFLOW = "barber-saas-workflow";

    /** CreateBarbershopRequest: name and city required, the rest null when not sent. */
    record NewBarbershop(String name, String address, String city, BigDecimal latitude, BigDecimal longitude,
                         String phone) { }

    /** On TRIAL until createdAt + 60 days (INV-SHOP-001); the same key returns the same barbershop. */
    Created<Barbershop> create(Caller caller, NewBarbershop data, String idempotencyKey);

    /** The compensation: physical delete while TRIAL and without barbers; an unknown id is already done. */
    void remove(Caller caller, UUID id);
}
