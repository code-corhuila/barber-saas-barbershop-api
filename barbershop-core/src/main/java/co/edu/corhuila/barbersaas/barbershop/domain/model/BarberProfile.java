package co.edu.corhuila.barbersaas.barbershop.domain.model;

import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The working profile of a barber (barbershop.barber_profile) with its specialties. The name and
 * photo live in identity_auth.app_user: only {@code userId} is kept here (DEC-SHOP-04, OQ-08).
 */
public final class BarberProfile {

    private static final BigDecimal MAX_RATING = BigDecimal.valueOf(5);

    private final UUID id;
    private final UUID barbershopId;
    private final UUID userId;
    private final int experienceYears;
    private final String bio;
    private final BigDecimal ratingAvg;
    private final int ratingCount;
    private final List<BarberSpecialty> specialties;

    public BarberProfile(UUID id, UUID barbershopId, UUID userId, int experienceYears, String bio,
                         BigDecimal ratingAvg, int ratingCount, List<BarberSpecialty> specialties) {
        this.id = Objects.requireNonNull(id);
        this.barbershopId = Objects.requireNonNull(barbershopId);
        this.userId = Objects.requireNonNull(userId);
        this.experienceYears = Rules.atLeast(experienceYears, 0, "years of experience");
        this.bio = Rules.optionalText(bio, 500, "bio");
        if (ratingAvg.signum() < 0 || ratingAvg.compareTo(MAX_RATING) > 0 || ratingCount < 0) {
            throw new BusinessRuleViolation("The rating must be between 0 and 5");
        }
        this.ratingAvg = ratingAvg;
        this.ratingCount = ratingCount;
        this.specialties = List.copyOf(specialties);
    }

    /** A new profile has no ratings yet: they are fed by reviews (06-data/models.md §11). */
    public static BarberProfile create(UUID id, UUID barbershopId, UUID userId, int experienceYears, String bio) {
        return new BarberProfile(id, barbershopId, userId, experienceYears, bio, BigDecimal.ZERO, 0, List.of());
    }

    /** A null argument was not sent; {@code bio} empty clears it. The rating is never edited here. */
    public BarberProfile edit(Optional<Integer> experienceYears, Optional<String> bio) {
        return new BarberProfile(id, barbershopId, userId,
                experienceYears == null ? this.experienceYears : experienceYears.orElse(0),
                bio == null ? this.bio : bio.orElse(null), ratingAvg, ratingCount, specialties);
    }

    public BarberProfile withSpecialties(List<BarberSpecialty> specialties) {
        return new BarberProfile(id, barbershopId, userId, experienceYears, bio, ratingAvg, ratingCount, specialties);
    }

    /** A BARBER may edit only the profile whose user is the token's subject. */
    public boolean belongsTo(UUID user) {
        return userId.equals(user);
    }

    public UUID id() { return id; }
    public UUID barbershopId() { return barbershopId; }
    public UUID userId() { return userId; }
    public int experienceYears() { return experienceYears; }
    public String bio() { return bio; }
    public BigDecimal ratingAvg() { return ratingAvg; }
    public int ratingCount() { return ratingCount; }
    public List<BarberSpecialty> specialties() { return specialties; }
}
