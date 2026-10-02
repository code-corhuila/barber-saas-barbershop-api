package co.edu.corhuila.barbersaas.barbershop.domain.model;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * A partial edit of a barbershop (PATCH /api/v1/barbershops/me). A null field was not sent and
 * keeps its value; for nullable columns, {@code Optional.empty()} clears it. There is no status
 * or plan here: only platform-admin changes them (DEC-SHOP-03).
 */
public record BarbershopChanges(String name, Optional<String> address, String city, Optional<BigDecimal> latitude,
                                Optional<BigDecimal> longitude, Optional<String> phone,
                                Optional<String> whatsappNumber, Optional<String> logoUrl, String timezone,
                                Integer cancellationPolicyHours) {

    public static BarbershopChanges none() {
        return new BarbershopChanges(null, null, null, null, null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return equals(none());
    }

    public BarbershopChanges withName(String v) {
        return new BarbershopChanges(v, address, city, latitude, longitude, phone, whatsappNumber, logoUrl, timezone,
                cancellationPolicyHours);
    }

    public BarbershopChanges withAddress(Optional<String> v) {
        return new BarbershopChanges(name, v, city, latitude, longitude, phone, whatsappNumber, logoUrl, timezone,
                cancellationPolicyHours);
    }

    public BarbershopChanges withCity(String v) {
        return new BarbershopChanges(name, address, v, latitude, longitude, phone, whatsappNumber, logoUrl, timezone,
                cancellationPolicyHours);
    }

    public BarbershopChanges withLatitude(Optional<BigDecimal> v) {
        return new BarbershopChanges(name, address, city, v, longitude, phone, whatsappNumber, logoUrl, timezone,
                cancellationPolicyHours);
    }

    public BarbershopChanges withLongitude(Optional<BigDecimal> v) {
        return new BarbershopChanges(name, address, city, latitude, v, phone, whatsappNumber, logoUrl, timezone,
                cancellationPolicyHours);
    }

    public BarbershopChanges withPhone(Optional<String> v) {
        return new BarbershopChanges(name, address, city, latitude, longitude, v, whatsappNumber, logoUrl, timezone,
                cancellationPolicyHours);
    }

    public BarbershopChanges withWhatsappNumber(Optional<String> v) {
        return new BarbershopChanges(name, address, city, latitude, longitude, phone, v, logoUrl, timezone,
                cancellationPolicyHours);
    }

    public BarbershopChanges withLogoUrl(Optional<String> v) {
        return new BarbershopChanges(name, address, city, latitude, longitude, phone, whatsappNumber, v, timezone,
                cancellationPolicyHours);
    }

    public BarbershopChanges withTimezone(String v) {
        return new BarbershopChanges(name, address, city, latitude, longitude, phone, whatsappNumber, logoUrl, v,
                cancellationPolicyHours);
    }

    public BarbershopChanges withCancellationPolicyHours(Integer v) {
        return new BarbershopChanges(name, address, city, latitude, longitude, phone, whatsappNumber, logoUrl, timezone,
                v);
    }
}
