package co.edu.corhuila.barbersaas.barbershop.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A service of the catalog (barbershop.service): what appointment books and takes its duration
 * and price from. Deactivating never deletes: past appointments keep resolving its id.
 */
public final class Service {

    public static final int MIN_DURATION_MINUTES = 5;

    private final UUID id;
    private final UUID barbershopId;
    private final String name;
    private final String description;
    private final int durationMinutes;
    private final long priceCents;
    private final boolean active;
    private final Instant createdAt;

    public Service(UUID id, UUID barbershopId, String name, String description, int durationMinutes,
                   long priceCents, boolean active, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.barbershopId = Objects.requireNonNull(barbershopId);
        this.name = Rules.requiredText(name, 100, "name");
        this.description = Rules.optionalText(description, 255, "description");
        this.durationMinutes = Rules.atLeast(durationMinutes, MIN_DURATION_MINUTES, "duration in minutes");
        // Money in integer cents, never a floating type (ADR-010).
        this.priceCents = Rules.atLeast(priceCents, 0, "price in cents");
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** FR-005: a new service belongs to the caller's barbershop and starts active. */
    public static Service create(UUID id, UUID barbershopId, String name, String description, int durationMinutes,
                                 long priceCents, Instant now) {
        return new Service(id, barbershopId, name, description, durationMinutes, priceCents, true, now);
    }

    /**
     * Replaces name, description, duration and price; {@code active} empty keeps the current state.
     * A new price never changes appointments already booked (INV-APPT-002): they keep their snapshot.
     */
    public Service edit(String name, String description, int durationMinutes, long priceCents,
                        Optional<Boolean> active) {
        return new Service(id, barbershopId, name, description, durationMinutes, priceCents,
                active.orElse(this.active), createdAt);
    }

    public UUID id() { return id; }
    public UUID barbershopId() { return barbershopId; }
    public String name() { return name; }
    public String description() { return description; }
    public int durationMinutes() { return durationMinutes; }
    public long priceCents() { return priceCents; }
    public boolean active() { return active; }
    public Instant createdAt() { return createdAt; }
}
