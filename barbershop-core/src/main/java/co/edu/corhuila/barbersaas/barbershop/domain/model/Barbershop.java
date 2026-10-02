package co.edu.corhuila.barbersaas.barbershop.domain.model;

import co.edu.corhuila.barbersaas.barbershop.domain.model.DomainException.BusinessRuleViolation;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** The tenant (barbershop.barbershop). Immutable: every change returns a new instance. */
public final class Barbershop {

    public static final Duration TRIAL = Duration.ofDays(60);
    public static final String DEFAULT_TIMEZONE = "America/Bogota";
    public static final int DEFAULT_CANCELLATION_POLICY_HOURS = 2;

    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    private final UUID id;
    private final String name;
    private final String address;
    private final String city;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final String phone;
    private final String whatsappNumber;
    private final String logoUrl;
    private final BarbershopStatus status;
    private final UUID planId;
    private final String timezone;
    private final int cancellationPolicyHours;
    private final Instant trialEndsAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Barbershop(UUID id, String name, String address, String city, BigDecimal latitude, BigDecimal longitude,
                      String phone, String whatsappNumber, String logoUrl, BarbershopStatus status, UUID planId,
                      String timezone, int cancellationPolicyHours, Instant trialEndsAt, Instant createdAt,
                      Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.name = Rules.requiredText(name, 120, "name");
        this.address = Rules.optionalText(address, 255, "address");
        this.city = Rules.requiredText(city, 80, "city");
        this.latitude = inRange(latitude, MAX_LATITUDE, "latitude");
        this.longitude = inRange(longitude, MAX_LONGITUDE, "longitude");
        this.phone = Rules.optionalText(phone, 20, "phone");
        this.whatsappNumber = Rules.optionalText(whatsappNumber, 20, "WhatsApp number");
        this.logoUrl = Rules.optionalText(logoUrl, 255, "logo URL");
        this.status = Objects.requireNonNull(status);
        this.planId = planId;
        this.timezone = requireZone(timezone);
        this.cancellationPolicyHours = Rules.atLeast(cancellationPolicyHours, 0, "cancellation policy in hours");
        this.trialEndsAt = Objects.requireNonNull(trialEndsAt);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /** INV-SHOP-001: a new barbershop is on TRIAL and its trial ends 60 days later, fixed once. */
    public static Barbershop register(UUID id, String name, String city, Instant now) {
        return new Barbershop(id, name, null, city, null, null, null, null, null, BarbershopStatus.TRIAL, null,
                DEFAULT_TIMEZONE, DEFAULT_CANCELLATION_POLICY_HOURS, now.plus(TRIAL), now, now);
    }

    /** Applies the fields that were sent; status, plan and the trial end never change here. */
    public Barbershop edit(BarbershopChanges c, Instant now) {
        return new Barbershop(id, pick(c.name(), name), pick(c.address(), address), pick(c.city(), city),
                pick(c.latitude(), latitude), pick(c.longitude(), longitude), pick(c.phone(), phone),
                pick(c.whatsappNumber(), whatsappNumber), pick(c.logoUrl(), logoUrl), status, planId,
                pick(c.timezone(), timezone),
                c.cancellationPolicyHours() == null ? cancellationPolicyHours : c.cancellationPolicyHours(),
                trialEndsAt, createdAt, now);
    }

    private static <T> T pick(T sent, T current) {
        return sent == null ? current : sent;
    }

    private static <T> T pick(Optional<T> sent, T current) {
        return sent == null ? current : sent.orElse(null);
    }

    private static BigDecimal inRange(BigDecimal value, BigDecimal max, String field) {
        if (value != null && (value.compareTo(max) > 0 || value.compareTo(max.negate()) < 0)) {
            throw new BusinessRuleViolation("The " + field + " must be between -" + max + " and " + max);
        }
        return value;
    }

    /** The IANA zone appointment and schedule interpret dates in; an unknown zone is rejected. */
    private static String requireZone(String timezone) {
        String z = Rules.requiredText(timezone, 50, "time zone");
        try {
            ZoneId.of(z);
        } catch (DateTimeException e) {
            throw new BusinessRuleViolation("The time zone is not a valid IANA zone");
        }
        return z;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public String address() { return address; }
    public String city() { return city; }
    public BigDecimal latitude() { return latitude; }
    public BigDecimal longitude() { return longitude; }
    public String phone() { return phone; }
    public String whatsappNumber() { return whatsappNumber; }
    public String logoUrl() { return logoUrl; }
    public BarbershopStatus status() { return status; }
    public UUID planId() { return planId; }
    public String timezone() { return timezone; }
    public int cancellationPolicyHours() { return cancellationPolicyHours; }
    public Instant trialEndsAt() { return trialEndsAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
