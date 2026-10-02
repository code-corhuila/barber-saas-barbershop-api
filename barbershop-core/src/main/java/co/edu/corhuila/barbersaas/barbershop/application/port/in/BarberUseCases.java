package co.edu.corhuila.barbersaas.barbershop.application.port.in;

import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import java.util.Optional;
import java.util.UUID;

/** Barber profiles and their specialties in the token's barbershop (barbershop-service.yaml, tag Barbers). */
public interface BarberUseCases {

    record NewProfile(UUID userId, int experienceYears, String bio) { }

    Page<BarberProfile> list(Caller caller, String specialty, Page.Request page);

    BarberProfile get(Caller caller, UUID id);

    /** ADMIN_BARBERSHOP; a user has at most one profile (422). */
    Created<BarberProfile> create(Caller caller, NewProfile profile, String idempotencyKey);

    /** ADMIN_BARBERSHOP any profile of their barbershop; BARBER only their own (403). Null = not sent. */
    BarberProfile edit(Caller caller, UUID id, Optional<Integer> experienceYears, Optional<String> bio);

    Page<BarberSpecialty> specialties(Caller caller, UUID barberProfileId, Page.Request page);

    /** ADMIN_BARBERSHOP, or the BARBER owner of the profile. */
    Created<BarberSpecialty> addSpecialty(Caller caller, UUID barberProfileId, String name, String idempotencyKey);

    /** ADMIN_BARBERSHOP, or the BARBER owner of the profile. Physical delete. */
    void removeSpecialty(Caller caller, UUID barberProfileId, UUID specialtyId);
}
