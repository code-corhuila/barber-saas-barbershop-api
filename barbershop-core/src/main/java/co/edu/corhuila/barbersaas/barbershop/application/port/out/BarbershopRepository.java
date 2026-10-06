package co.edu.corhuila.barbersaas.barbershop.application.port.out;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.PlatformBarbershopUseCases.Filter;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import java.util.Optional;
import java.util.UUID;

/** Persistence of barbershop.barbershop. */
public interface BarbershopRepository {

    /** Only TRIAL and ACTIVE; by approximate distance when the search has coordinates, else most recent first. */
    Page<Barbershop> searchVisible(Search search, Page.Request page);

    Optional<Barbershop> findById(UUID id);

    /** Every barbershop in any status, most recent first, for platform-admin (DEC-SHOP-06). */
    Page<Barbershop> searchAll(Filter filter, Page.Request page);

    /**
     * Writes status, plan and updatedAt only while the stored status is still {@code expectedStatus}.
     * False when another change came first: nothing is written.
     */
    boolean updateLifecycle(Barbershop barbershop, BarbershopStatus expectedStatus);

    void update(Barbershop barbershop);

    Optional<Idempotency.Stored> findKey(String key, String operation);

    /** Writes the barbershop and its idempotency key in ONE transaction. */
    void saveNew(Barbershop barbershop, Idempotency.Key key);

    /**
     * Deletes it in one statement only while it is TRIAL and has no barber profile, so a barber created
     * meanwhile is never removed with it (its services go with it, ON DELETE CASCADE). False when not deleted.
     */
    boolean deleteIfRemovable(UUID id);
}
