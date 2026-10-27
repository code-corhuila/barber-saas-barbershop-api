package co.edu.corhuila.barbersaas.barbershop.adapter.out.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.application.port.out.Plans.Plan;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Plans.Unavailable;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HttpPlans against a local server standing in for platform-admin-api (DEC-SHOP-07). */
class HttpPlansTest {

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>("{}");
    private final AtomicReference<String> request = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                    + exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), status.get() == 200 ? bytes.length : -1);
            if (status.get() == 200) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private HttpPlans plans() {
        return new HttpPlans("http://127.0.0.1:" + server.getAddress().getPort() + "/", "service-token");
    }

    @Test
    void a_plan_is_read_with_the_internal_route_and_this_service_token() {
        UUID id = UUID.randomUUID();
        body.set("{\"id\":\"" + id + "\",\"name\":\"Basico\",\"priceCents\":4990000,\"maxBarbers\":2,"
                + "\"featuresJson\":null,\"isActive\":true,\"createdAt\":\"2026-10-01T00:00:00Z\"}");

        Plan plan = plans().find(id).orElseThrow();

        assertEquals("GET /internal/v1/plans/" + id + " Bearer service-token", request.get());
        assertEquals(new Plan(id, "Basico", 2), plan);
    }

    @Test
    void a_404_means_no_such_plan() {
        status.set(404);

        assertTrue(plans().find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void any_other_answer_no_answer_or_no_configuration_is_unavailable() {
        status.set(403);
        assertThrows(Unavailable.class, () -> plans().find(UUID.randomUUID()));
        status.set(200);
        body.set("{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"Pro\"}");
        assertThrows(Unavailable.class, () -> plans().find(UUID.randomUUID()));
        body.set("not json");
        assertThrows(Unavailable.class, () -> plans().find(UUID.randomUUID()));

        assertThrows(Unavailable.class, () -> new HttpPlans("http://127.0.0.1:1", "t").find(UUID.randomUUID()));
        assertThrows(Unavailable.class, () -> new HttpPlans("", "t").find(UUID.randomUUID()));
    }
}
