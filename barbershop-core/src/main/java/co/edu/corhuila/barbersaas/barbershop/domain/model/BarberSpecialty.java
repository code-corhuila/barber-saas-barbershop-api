package co.edu.corhuila.barbersaas.barbershop.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A specialty of a barber (barbershop.barber_specialty). Removing it is a physical delete. */
public record BarberSpecialty(UUID id, UUID barberProfileId, String specialtyName) {

    public BarberSpecialty {
        Objects.requireNonNull(id);
        Objects.requireNonNull(barberProfileId);
        specialtyName = Rules.requiredText(specialtyName, 80, "specialty name");
    }

    public static BarberSpecialty create(UUID id, UUID barberProfileId, String specialtyName) {
        return new BarberSpecialty(id, barberProfileId, specialtyName);
    }
}
