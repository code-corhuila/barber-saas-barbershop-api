package co.edu.corhuila.barbersaas.barbershop.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * barber-saas-docs#164: service tokens (role SERVICE) are only for /internal/v1/**. On the protected
 * routes of the app they answer 401, so a copied service token cannot act as an owner. The routes are
 * split by caller, not by network: the same token keeps working on the internal routes.
 */
class ServiceTokenHttpTest extends HttpTest {

    private static final List<String> SERVICES = List.of("barber-saas-workflow", "barber-saas-platform-admin-api",
            "barber-saas-appointment-api", "barber-saas-schedule-api", "barber-saas-worker");

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
        return request.header("Idempotency-Key", "key-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @Test
    void a_service_token_answers_401_on_every_protected_route_of_the_app() throws Exception {
        String id = UUID.randomUUID().toString();
        List<Supplier<MockHttpServletRequestBuilder>> routes = List.of(
                () -> get("/api/v1/barbershops/me"),
                () -> json(patch("/api/v1/barbershops/me")),
                () -> get("/api/v1/barbers"),
                () -> json(post("/api/v1/barbers")),
                () -> get("/api/v1/barbers/" + id),
                () -> json(patch("/api/v1/barbers/" + id)),
                () -> get("/api/v1/barbers/" + id + "/specialties"),
                () -> json(post("/api/v1/barbers/" + id + "/specialties")),
                () -> delete("/api/v1/barbers/" + id + "/specialties/" + UUID.randomUUID()),
                () -> get("/api/v1/services"),
                () -> json(post("/api/v1/services")),
                () -> get("/api/v1/services/" + id),
                () -> json(put("/api/v1/services/" + id)));

        for (String service : SERVICES) {
            for (Supplier<MockHttpServletRequestBuilder> route : routes) {
                http.perform(route.get().header("Authorization", serviceBearer(service)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
            }
        }
    }

    @Test
    void the_same_service_token_still_works_on_the_internal_routes() throws Exception {
        http.perform(post("/internal/v1/barbershops").header("Authorization", serviceBearer("barber-saas-workflow"))
                        .header("Idempotency-Key", "signup-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"La Esquina\",\"city\":\"Neiva\"}"))
                .andExpect(status().isCreated());
    }
}
