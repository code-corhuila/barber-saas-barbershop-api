package co.edu.corhuila.barbersaas.barbershop.application.usecase;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.ServiceRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test doubles of the outbound ports: the use cases are tested without Spring and without a database. */
final class Fakes {

    private Fakes() {
    }

    static final class Barbershops implements BarbershopRepository {
        final Map<UUID, Barbershop> rows = new LinkedHashMap<>();

        Barbershop add(Barbershop b) {
            rows.put(b.id(), b);
            return b;
        }

        @Override
        public Page<Barbershop> searchVisible(Search search, Page.Request page) {
            return Page.of(rows.values().stream()
                    .filter(b -> b.status().visible())
                    .filter(b -> search.city() == null || b.city().equalsIgnoreCase(search.city()))
                    .sorted(Comparator.comparing(Barbershop::createdAt).reversed())
                    .toList(), page);
        }

        @Override
        public Optional<Barbershop> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public void update(Barbershop b) {
            rows.put(b.id(), b);
        }
    }

    static final class Services implements ServiceRepository {
        final Map<UUID, Service> rows = new LinkedHashMap<>();
        final Map<String, Idempotency.Stored> keys = new HashMap<>();

        Service add(Service s) {
            rows.put(s.id(), s);
            return s;
        }

        @Override
        public Page<Service> page(UUID barbershopId, Boolean active, Page.Request page) {
            return Page.of(rows.values().stream()
                    .filter(s -> s.barbershopId().equals(barbershopId))
                    .filter(s -> active == null || s.active() == active)
                    .toList(), page);
        }

        @Override
        public Optional<Service> findById(UUID barbershopId, UUID id) {
            return Optional.ofNullable(rows.get(id)).filter(s -> s.barbershopId().equals(barbershopId));
        }

        @Override
        public Optional<Idempotency.Stored> findKey(String key, String operation) {
            return Optional.ofNullable(keys.get(operation + " " + key));
        }

        @Override
        public void saveNew(Service s, Idempotency.Key key) {
            rows.put(s.id(), s);
            keys.put(key.operation() + " " + key.key(), new Idempotency.Stored(s.id(), key.requestHash()));
        }

        @Override
        public void update(Service s) {
            rows.put(s.id(), s);
        }
    }

    static final class Barbers implements BarberRepository {
        final Map<UUID, BarberProfile> rows = new LinkedHashMap<>();
        final Map<UUID, BarberSpecialty> specialties = new LinkedHashMap<>();
        final Map<String, Idempotency.Stored> keys = new HashMap<>();

        BarberProfile add(BarberProfile p) {
            rows.put(p.id(), p);
            return p;
        }

        private BarberProfile withSpecialties(BarberProfile p) {
            return p.withSpecialties(specialties.values().stream()
                    .filter(s -> s.barberProfileId().equals(p.id())).toList());
        }

        @Override
        public Page<BarberProfile> page(UUID barbershopId, String specialty, Page.Request page) {
            return Page.of(rows.values().stream()
                    .filter(p -> p.barbershopId().equals(barbershopId))
                    .map(this::withSpecialties)
                    .filter(p -> specialty == null
                            || p.specialties().stream().anyMatch(s -> s.specialtyName().equals(specialty)))
                    .toList(), page);
        }

        @Override
        public Optional<BarberProfile> findById(UUID barbershopId, UUID id) {
            return Optional.ofNullable(rows.get(id)).filter(p -> p.barbershopId().equals(barbershopId))
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
        public void saveNew(BarberProfile p, Idempotency.Key key) {
            if (existsForUser(p.userId())) {
                throw new UserAlreadyHasProfile();
            }
            rows.put(p.id(), p);
            keys.put(key.operation() + " " + key.key(), new Idempotency.Stored(p.id(), key.requestHash()));
        }

        @Override
        public void update(BarberProfile p) {
            rows.put(p.id(), p);
        }

        @Override
        public Page<BarberSpecialty> specialties(UUID barberProfileId, Page.Request page) {
            return Page.of(new ArrayList<>(specialties.values().stream()
                    .filter(s -> s.barberProfileId().equals(barberProfileId)).toList()), page);
        }

        @Override
        public Optional<BarberSpecialty> findSpecialty(UUID barberProfileId, UUID specialtyId) {
            return Optional.ofNullable(specialties.get(specialtyId))
                    .filter(s -> s.barberProfileId().equals(barberProfileId));
        }

        @Override
        public void saveNewSpecialty(BarberSpecialty s, Idempotency.Key key) {
            specialties.put(s.id(), s);
            keys.put(key.operation() + " " + key.key(), new Idempotency.Stored(s.id(), key.requestHash()));
        }

        @Override
        public void deleteSpecialty(UUID specialtyId) {
            specialties.remove(specialtyId);
        }

        List<BarberSpecialty> allSpecialties() {
            return List.copyOf(specialties.values());
        }
    }
}
