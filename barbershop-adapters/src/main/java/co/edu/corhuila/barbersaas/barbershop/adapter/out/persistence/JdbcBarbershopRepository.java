package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.BarbershopUseCases.Search;
import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import co.edu.corhuila.barbersaas.barbershop.domain.model.BarbershopStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads and writes the schema owned by barber-saas-barbershop-db. It knows SQL; the domain does not. */
public class JdbcBarbershopRepository implements BarbershopRepository {

    private static final String COLUMNS = "id, name, address, city, latitude, longitude, phone, whatsapp_number, "
            + "logo_url, status, plan_id, timezone, cancellation_policy_hours, trial_ends_at, created_at, updated_at";

    private final JdbcTemplate jdbc;

    public JdbcBarbershopRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page<Barbershop> searchVisible(Search search, Page.Request page) {
        StringBuilder from = new StringBuilder("FROM barbershop.barbershop WHERE status IN ('TRIAL', 'ACTIVE')");
        List<Object> args = new ArrayList<>();
        if (search.city() != null) {
            from.append(" AND lower(city) = lower(?)");
            args.add(search.city());
        }
        JdbcPages.Query query = new JdbcPages.Query(from.toString(), args, "ORDER BY created_at DESC, id");
        if (search.latitude() != null) {
            // Same approximation as Distance: closest first, barbershops without coordinates last.
            query = new JdbcPages.Query(from.toString(), args, "ORDER BY (latitude IS NULL), "
                    + "power(latitude - ?, 2) + power((longitude - ?) * cos(radians(?::float8)), 2), id",
                    List.of(search.latitude(), search.longitude(), search.latitude()));
        }
        return JdbcPages.page(jdbc, COLUMNS, query, (rs, n) -> map(rs), page);
    }

    @Override
    public Optional<Barbershop> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM barbershop.barbershop WHERE id = ?", (rs, n) -> map(rs), id)
                .stream().findFirst();
    }

    /** Status, plan and the trial end are never written here (DEC-SHOP-03, INV-SHOP-001). */
    @Override
    public void update(Barbershop b) {
        jdbc.update("UPDATE barbershop.barbershop SET name = ?, address = ?, city = ?, latitude = ?, longitude = ?, "
                        + "phone = ?, whatsapp_number = ?, logo_url = ?, timezone = ?, cancellation_policy_hours = ?, "
                        + "updated_at = ? WHERE id = ?",
                b.name(), b.address(), b.city(), b.latitude(), b.longitude(), b.phone(), b.whatsappNumber(),
                b.logoUrl(), b.timezone(), b.cancellationPolicyHours(), Timestamp.from(b.updatedAt()), b.id());
    }

    private static Barbershop map(ResultSet rs) throws SQLException {
        return new Barbershop(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("address"),
                rs.getString("city"), rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"),
                rs.getString("phone"), rs.getString("whatsapp_number"), rs.getString("logo_url"),
                BarbershopStatus.valueOf(rs.getString("status")), rs.getObject("plan_id", UUID.class),
                rs.getString("timezone"), rs.getInt("cancellation_policy_hours"),
                rs.getTimestamp("trial_ends_at").toInstant(), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
