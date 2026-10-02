package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarberRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberProfile;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarberSpecialty;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Reads and writes barbershop.barber_profile and barbershop.barber_specialty; profiles filter by the barbershop. */
public class JdbcBarberRepository implements BarberRepository {

    private static final String COLUMNS =
            "p.id, p.barbershop_id, p.user_id, p.experience_years, p.bio, p.rating_avg, p.rating_count";
    private static final String SPECIALTY_COLUMNS = "id, barber_profile_id, specialty_name";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcBarberRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Page<BarberProfile> page(UUID barbershopId, String specialty, Page.Request page) {
        StringBuilder from = new StringBuilder("FROM barbershop.barber_profile p WHERE p.barbershop_id = ?");
        List<Object> args = new ArrayList<>(List.of(barbershopId));
        if (specialty != null) {
            from.append(" AND EXISTS (SELECT 1 FROM barbershop.barber_specialty s "
                    + "WHERE s.barber_profile_id = p.id AND s.specialty_name = ?)");
            args.add(specialty);
        }
        // barber_profile has no created_at: the order is stable by id.
        Page<BarberProfile> profiles = JdbcPages.page(jdbc, COLUMNS,
                new JdbcPages.Query(from.toString(), args, "ORDER BY p.id"), (rs, n) -> map(rs), page);
        Map<UUID, List<BarberSpecialty>> byProfile = specialtiesOf(profiles.items().stream()
                .map(BarberProfile::id).toList());
        return profiles.map(p -> p.withSpecialties(byProfile.getOrDefault(p.id(), List.of())));
    }

    @Override
    public Optional<BarberProfile> findById(UUID barbershopId, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM barbershop.barber_profile p WHERE p.barbershop_id = ? "
                        + "AND p.id = ?", (rs, n) -> map(rs), barbershopId, id).stream().findFirst()
                .map(p -> p.withSpecialties(specialtiesOf(List.of(p.id())).getOrDefault(p.id(), List.of())));
    }

    @Override
    public boolean existsForUser(UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM barbershop.barber_profile WHERE user_id = ?)", Boolean.class, userId));
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return JdbcIdempotency.find(jdbc, key, operation);
    }

    @Override
    public void saveNew(BarberProfile p, Idempotency.Key key) {
        try {
            tx.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO barbershop.barber_profile (id, barbershop_id, user_id, experience_years, bio, "
                                + "rating_avg, rating_count) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        p.id(), p.barbershopId(), p.userId(), p.experienceYears(), p.bio(), p.ratingAvg(),
                        p.ratingCount());
                JdbcIdempotency.insert(jdbc, key, p.id());
            });
        } catch (DuplicateKeyException e) {
            if (String.valueOf(e.getMessage()).contains("uq_barber_profile_user")) {
                throw new UserAlreadyHasProfile();
            }
            throw e;
        }
    }

    /** The rating is never written here: it is fed by reviews (06-data/models.md §11). */
    @Override
    public void update(BarberProfile p) {
        jdbc.update("UPDATE barbershop.barber_profile SET experience_years = ?, bio = ? "
                + "WHERE barbershop_id = ? AND id = ?", p.experienceYears(), p.bio(), p.barbershopId(), p.id());
    }

    @Override
    public Page<BarberSpecialty> specialties(UUID barberProfileId, Page.Request page) {
        return JdbcPages.page(jdbc, SPECIALTY_COLUMNS, new JdbcPages.Query(
                        "FROM barbershop.barber_specialty WHERE barber_profile_id = ?", List.of(barberProfileId),
                        "ORDER BY specialty_name, id"), (rs, n) -> mapSpecialty(rs), page);
    }

    @Override
    public Optional<BarberSpecialty> findSpecialty(UUID barberProfileId, UUID specialtyId) {
        return jdbc.query("SELECT " + SPECIALTY_COLUMNS + " FROM barbershop.barber_specialty "
                        + "WHERE barber_profile_id = ? AND id = ?", (rs, n) -> mapSpecialty(rs), barberProfileId,
                specialtyId).stream().findFirst();
    }

    @Override
    public void saveNewSpecialty(BarberSpecialty s, Idempotency.Key key) {
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO barbershop.barber_specialty (id, barber_profile_id, specialty_name) "
                    + "VALUES (?, ?, ?)", s.id(), s.barberProfileId(), s.specialtyName());
            JdbcIdempotency.insert(jdbc, key, s.id());
        });
    }

    @Override
    public void deleteSpecialty(UUID specialtyId) {
        jdbc.update("DELETE FROM barbershop.barber_specialty WHERE id = ?", specialtyId);
    }

    /** One query for the specialties of every profile of a page, instead of one per profile. */
    private Map<UUID, List<BarberSpecialty>> specialtiesOf(List<UUID> profileIds) {
        if (profileIds.isEmpty()) {
            return Map.of();
        }
        String marks = String.join(", ", Collections.nCopies(profileIds.size(), "?"));
        return jdbc.query("SELECT " + SPECIALTY_COLUMNS + " FROM barbershop.barber_specialty "
                                + "WHERE barber_profile_id IN (" + marks + ") ORDER BY specialty_name, id",
                        (rs, n) -> mapSpecialty(rs), profileIds.toArray())
                .stream().collect(Collectors.groupingBy(BarberSpecialty::barberProfileId));
    }

    private static BarberProfile map(ResultSet rs) throws SQLException {
        return new BarberProfile(rs.getObject("id", UUID.class), rs.getObject("barbershop_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getInt("experience_years"), rs.getString("bio"),
                rs.getBigDecimal("rating_avg"), rs.getInt("rating_count"), List.of());
    }

    private static BarberSpecialty mapSpecialty(ResultSet rs) throws SQLException {
        return new BarberSpecialty(rs.getObject("id", UUID.class), rs.getObject("barber_profile_id", UUID.class),
                rs.getString("specialty_name"));
    }
}
