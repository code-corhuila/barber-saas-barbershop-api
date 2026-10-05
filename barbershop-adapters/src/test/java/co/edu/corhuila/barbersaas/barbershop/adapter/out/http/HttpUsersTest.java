package co.edu.corhuila.barbersaas.barbershop.adapter.out.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.barbersaas.barbershop.application.port.out.Users.Unavailable;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Users.User;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HttpUsers against a local server standing in for identity-auth-api. */
class HttpUsersTest {

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

    private HttpUsers users() {
        return new HttpUsers("http://127.0.0.1:" + server.getAddress().getPort() + "/", "service-token");
    }

    @Test
    void a_user_is_read_with_the_internal_route_and_this_service_token() {
        UUID id = UUID.randomUUID();
        UUID shop = UUID.randomUUID();
        body.set("{\"id\":\"" + id + "\",\"fullName\":\"Juan Pérez\",\"profilePhotoUrl\":null,\"role\":\"BARBER\","
                + "\"barbershopId\":\"" + shop + "\",\"isActive\":true}");

        User user = users().find(id).orElseThrow();

        assertEquals("GET /internal/v1/users/" + id + " Bearer service-token", request.get());
        assertEquals("Juan Pérez", user.fullName());
        assertNull(user.profilePhotoUrl());
        assertEquals("BARBER", user.role());
        assertEquals(shop, user.barbershopId());
        assertTrue(user.active());
    }

    @Test
    void a_user_without_a_barbershop_is_read_too() {
        body.set("{\"id\":\"" + UUID.randomUUID() + "\",\"fullName\":\"Luis\",\"role\":\"CLIENT\","
                + "\"barbershopId\":null,\"isActive\":false}");

        User user = users().find(UUID.randomUUID()).orElseThrow();

        assertNull(user.barbershopId());
        assertFalse(user.active());
    }

    @Test
    void a_404_means_no_such_user() {
        status.set(404);

        assertTrue(users().find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void any_other_answer_no_answer_or_no_configuration_is_unavailable() {
        status.set(403);
        assertThrows(Unavailable.class, () -> users().find(UUID.randomUUID()));
        status.set(200);
        body.set("not json");
        assertThrows(Unavailable.class, () -> users().find(UUID.randomUUID()));

        assertThrows(Unavailable.class, () -> new HttpUsers("http://127.0.0.1:1", "t").find(UUID.randomUUID()));
        assertThrows(Unavailable.class, () -> new HttpUsers("", "t").find(UUID.randomUUID()));
        assertThrows(Unavailable.class, () -> new HttpUsers("http://127.0.0.1:1", " ").find(UUID.randomUUID()));
    }
}
