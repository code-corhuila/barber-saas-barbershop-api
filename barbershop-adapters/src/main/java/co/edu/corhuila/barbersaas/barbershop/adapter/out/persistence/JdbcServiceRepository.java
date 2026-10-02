package co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.Page;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Idempotency;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.ServiceRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Service;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Reads and writes barbershop.service; every query filters by the barbershop. */
public class JdbcServiceRepository implements ServiceRepository {

    private static final String COLUMNS =
            "id, barbershop_id, name, description, duration_minutes, price_cents, is_active, created_at";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcServiceRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Page<Service> page(UUID barbershopId, Boolean active, Page.Request page) {
        StringBuilder from = new StringBuilder("FROM barbershop.service WHERE barbershop_id = ?");
        List<Object> args = new ArrayList<>(List.of(barbershopId));
        if (active != null) {
            from.append(" AND is_active = ?");
            args.add(active);
        }
        return JdbcPages.page(jdbc, COLUMNS, new JdbcPages.Query(from.toString(), args, "ORDER BY created_at DESC, id"),
                (rs, n) -> map(rs), page);
    }

    @Override
    public Optional<Service> findById(UUID barbershopId, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM barbershop.service WHERE barbershop_id = ? AND id = ?",
                (rs, n) -> map(rs), barbershopId, id).stream().findFirst();
    }

    @Override
    public Optional<Idempotency.Stored> findKey(String key, String operation) {
        return JdbcIdempotency.find(jdbc, key, operation);
    }

    @Override
    public void saveNew(Service s, Idempotency.Key key) {
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO barbershop.service (id, barbershop_id, name, description, duration_minutes, "
                            + "price_cents, is_active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    s.id(), s.barbershopId(), s.name(), s.description(), s.durationMinutes(), s.priceCents(),
                    s.active(), Timestamp.from(s.createdAt()));
            JdbcIdempotency.insert(jdbc, key, s.id());
        });
    }

    @Override
    public void update(Service s) {
        jdbc.update("UPDATE barbershop.service SET name = ?, description = ?, duration_minutes = ?, price_cents = ?, "
                        + "is_active = ? WHERE barbershop_id = ? AND id = ?",
                s.name(), s.description(), s.durationMinutes(), s.priceCents(), s.active(), s.barbershopId(), s.id());
    }

    private static Service map(ResultSet rs) throws SQLException {
        return new Service(rs.getObject("id", UUID.class), rs.getObject("barbershop_id", UUID.class),
                rs.getString("name"), rs.getString("description"), rs.getInt("duration_minutes"),
                rs.getLong("price_cents"), rs.getBoolean("is_active"), rs.getTimestamp("created_at").toInstant());
    }
}
