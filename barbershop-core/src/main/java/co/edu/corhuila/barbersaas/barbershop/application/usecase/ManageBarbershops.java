package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller.Role;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.ServiceRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopChanges;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.time.Clock;
import java.util.UUID;

public class ManageBarbershops implements BarbershopUseCases {

    private final BarbershopRepository barbershops;
    private final ServiceRepository services;
    private final BarberRepository barbers;
    private final Clock clock;

    public ManageBarbershops(BarbershopRepository barbershops, ServiceRepository services, BarberRepository barbers,
                             Clock clock) {
        this.barbershops = barbershops;
        this.services = services;
        this.barbers = barbers;
        this.clock = clock;
    }

    @Override
    public Page<Barbershop> search(Search search, Page.Request page) {
        return barbershops.searchVisible(search, page);
    }

    @Override
    public Barbershop getVisible(UUID id) {
        return barbershops.findById(id)
                .filter(b -> b.status().visible())
                .orElseThrow(() -> new NotFound("Barbershop"));
    }

    @Override
    public Page<Service> listVisibleServices(UUID barbershopId, Page.Request page) {
        return services.page(getVisible(barbershopId).id(), true, page);
    }

    @Override
    public Page<BarberProfile> listVisibleBarbers(UUID barbershopId, Page.Request page) {
        return barbers.page(getVisible(barbershopId).id(), null, page);
    }

    @Override
    public Barbershop getMine(Caller caller) {
        caller.require(Role.ADMIN_BARBERSHOP, Role.BARBER, Role.CLIENT);
        return mine(caller);
    }

    @Override
    public Barbershop editMine(Caller caller, BarbershopChanges changes) {
        caller.require(Role.ADMIN_BARBERSHOP);
        Barbershop edited = mine(caller).edit(changes, clock.instant());
        barbershops.update(edited);
        return edited;
    }

    private Barbershop mine(Caller caller) {
        return barbershops.findById(caller.tenant()).orElseThrow(() -> new NotFound("Barbershop"));
    }
}
