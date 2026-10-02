package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Used when DATABASE_URL is empty: the service starts and its HTTP contract can be tested without a database. */
public class InMemoryBarberRepository implements BarberRepository {

    private final Map<UUID, BarberProfile> rows = new ConcurrentHashMap<>();
    private final Map<UUID, BarberSpecialty> specialties = new ConcurrentHashMap<>();
    private final Map<String, Idempotency.Stored> keys = new ConcurrentHashMap<>();

    private BarberProfile withSpecialties(BarberProfile p) {
        return p.withSpecialties(specialties.values().stream()
                .filter(s -> s.barberProfileId().equals(p.id()))
                .sorted(Comparator.comparing(BarberSpecialty::specialtyName))
                .toList());
    }

    @Override
    public Page<BarberProfile> page(UUID barbershopId, String specialty, Page.Request page) {
        return Page.of(rows.values().stream()
                .filter(p -> p.barbershopId().equals(barbershopId))
                .map(this::withSpecialties)
                .filter(p -> specialty == null
                        || p.specialties().stream().anyMatch(s -> s.specialtyName().equals(specialty)))
                .sorted(Comparator.comparing(BarberProfile::id))
                .toList(), page);
    }

    @Override
    public Optional<BarberProfile> findById(UUID barbershopId, UUID id) {
        return Optional.ofNullable(rows.get(id))
                .filter(p -> p.barbershopId().equals(barbershopId))
                .map(this::withSpecialties);
    }

    @Override
    public boolean existsForUser(UUID userId) {
        return rows.values().stream().anyMatch(p -> p.userId().equals(userId));
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return Optional.ofNullable(keys.get(operation + " " + key));
    }

    @Override
    public synchronized void saveNew(BarberProfile profile, Idempotency.Key key) {
        if (existsForUser(profile.userId())) {
            throw new UserAlreadyHasProfile();
        }
        rows.put(profile.id(), profile);
        keys.put(key.operation() + " " + key.key(), new Idempotency.Stored(profile.id(), key.requestHash()));
    }

    @Override
    public void update(BarberProfile profile) {
        rows.put(profile.id(), profile);
    }

    @Override
    public Page<BarberSpecialty> specialties(UUID barberProfileId, Page.Request page) {
        return Page.of(specialties.values().stream()
                .filter(s -> s.barberProfileId().equals(barberProfileId))
                .sorted(Comparator.comparing(BarberSpecialty::specialtyName))
                .toList(), page);
    }

    @Override
    public Optional<BarberSpecialty> findSpecialty(UUID barberProfileId, UUID specialtyId) {
        return Optional.ofNullable(specialties.get(specialtyId))
                .filter(s -> s.barberProfileId().equals(barberProfileId));
    }

    @Override
    public synchronized void saveNewSpecialty(BarberSpecialty specialty, Idempotency.Key key) {
        specialties.put(specialty.id(), specialty);
        keys.put(key.operation() + " " + key.key(), new Idempotency.Stored(specialty.id(), key.requestHash()));
    }

    @Override
    public void deleteSpecialty(UUID specialtyId) {
        specialties.remove(specialtyId);
    }
}
