package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.ApplicationException.NotFound;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.PlatformBarbershopUseCases;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.InvalidStatusTransition;
import java.time.Clock;
import java.util.UUID;
import java.util.function.UnaryOperator;

public class AdministerBarbershops implements PlatformBarbershopUseCases {

    private final BarbershopRepository barbershops;
    private final Clock clock;

    public AdministerBarbershops(BarbershopRepository barbershops, Clock clock) {
        this.barbershops = barbershops;
        this.clock = clock;
    }

    @Override
    public Page<Barbershop> list(Caller caller, Filter filter, Page.Request page) {
        caller.requireService(PLATFORM_ADMIN);
        return barbershops.searchAll(filter, page);
    }

    @Override
    public Barbershop get(Caller caller, UUID id) {
        caller.requireService(PLATFORM_ADMIN);
        return find(id);
    }

    @Override
    public Barbershop changeStatus(Caller caller, UUID id, BarbershopStatus target) {
        caller.requireService(PLATFORM_ADMIN);
        return change(id, b -> b.changeStatus(target, clock.instant()));
    }

    @Override
    public Barbershop assignPlan(Caller caller, UUID id, UUID planId) {
        caller.requireService(PLATFORM_ADMIN);
        return change(id, b -> b.assignPlan(planId, clock.instant()));
    }

    /**
     * The write only succeeds if the status is still the one read: two administrators acting at once
     * never overwrite each other, and the loser reads it again instead of reviving a cancelled one.
     */
    private Barbershop change(UUID id, UnaryOperator<Barbershop> rule) {
        Barbershop current = find(id);
        Barbershop changed = rule.apply(current);
        if (changed == current) {
            return current;
        }
        if (!barbershops.updateLifecycle(changed, current.status())) {
            throw new InvalidStatusTransition("The barbershop changed meanwhile; read it again");
        }
        return changed;
    }

    private Barbershop find(UUID id) {
        return barbershops.findById(id).orElseThrow(() -> new NotFound("Barbershop"));
    }
}
