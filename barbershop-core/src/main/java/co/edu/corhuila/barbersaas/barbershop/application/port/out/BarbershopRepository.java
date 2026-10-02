package co.edu.corhuila.barbersaas.barbershop.application.port.out;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import java.util.Optional;
import java.util.UUID;

/** Persistence of barbershop.barbershop. */
public interface BarbershopRepository {

    /** Only TRIAL and ACTIVE; by approximate distance when the search has coordinates, else most recent first. */
    Page<Barbershop> searchVisible(Search search, Page.Request page);

    Optional<Barbershop> findById(UUID id);

    void update(Barbershop barbershop);
}
