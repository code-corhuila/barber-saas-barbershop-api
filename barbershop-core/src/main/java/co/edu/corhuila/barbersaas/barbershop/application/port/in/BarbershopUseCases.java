package co.edu.corhuila.barbersaas.barbershop.application.port.in;

import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopChanges;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.math.BigDecimal;
import java.util.UUID;

/** The barbershop and its anonymous discovery catalog (barbershop-service.yaml, tag Barbershops). */
public interface BarbershopUseCases {

    /** Every field is optional; the coordinates come both or neither, and with them the closest come first. */
    record Search(String city, BigDecimal latitude, BigDecimal longitude) { }

    /** DEC-SHOP-02: only TRIAL and ACTIVE barbershops, without a token. */
    Page<Barbershop> search(Search search, Page.Request page);

    /** 404 when it does not exist or is SUSPENDED/CANCELLED. */
    Barbershop getVisible(UUID id);

    Page<Service> listVisibleServices(UUID barbershopId, Page.Request page);

    Page<BarberProfile> listVisibleBarbers(UUID barbershopId, Page.Request page);

    /** The token's barbershop: ADMIN_BARBERSHOP, BARBER and CLIENT (with a tenant). */
    Barbershop getMine(Caller caller);

    /** ADMIN_BARBERSHOP only; status and plan never change here (DEC-SHOP-03). */
    Barbershop editMine(Caller caller, BarbershopChanges changes);
}
