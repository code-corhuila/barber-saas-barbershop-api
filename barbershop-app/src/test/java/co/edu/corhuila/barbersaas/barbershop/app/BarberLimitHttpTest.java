package co.edu.corhuila.barbersaas.barbershop.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence.InMemoryBarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** DEC-SHOP-07 over HTTP: activeBarbers on every read, and the plan's barber limit on POST /api/v1/barbers. */
class BarberLimitHttpTest extends HttpTest {

    @Autowired
    BarbershopRepository barbershops;

    private final String city = "Neiva-" + UUID.randomUUID();

    private UUID barbershopOn(UUID plan) {
        Barbershop b = Barbershop.register(UUID.randomUUID(), "El Clásico", city, Instant.now())
                .assignPlan(plan, Instant.now());
        ((InMemoryBarbershopRepository) barbershops).put(b);
        return b.id();
    }

    private ResultActions addBarber(UUID shop) throws Exception {
        return http.perform(post("/api/v1/barbers").header("Authorization", bearer("ADMIN_BARBERSHOP", shop))
                .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + identityBarber("Juan Perez", shop) + "\"}"));
    }

    @Test
    void the_plan_allows_its_barbers_and_refuses_one_more() throws Exception {
        UUID shop = barbershopOn(platformPlan("Basico", 2));

        addBarber(shop).andExpect(status().isCreated());
        addBarber(shop).andExpect(status().isCreated());
        addBarber(shop).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Tu plan permite 2 barberos"));
    }

    @Test
    void every_read_of_the_barbershop_counts_its_barbers() throws Exception {
        UUID shop = barbershopOn(platformPlan("Pro", 6));
        String owner = bearer("ADMIN_BARBERSHOP", shop);
        addBarber(shop).andExpect(status().isCreated());
        addBarber(shop).andExpect(status().isCreated());

        http.perform(get("/api/v1/barbershops/me").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeBarbers").value(2));
        http.perform(patch("/api/v1/barbershops/me").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"3001234567\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeBarbers").value(2));
        http.perform(get("/api/v1/barbershops/" + shop).header("Authorization", bearer("CLIENT", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeBarbers").value(2));
        http.perform(get("/api/v1/barbershops").param("city", city).header("Authorization", bearer("CLIENT", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].activeBarbers").value(2));
        http.perform(get("/internal/v1/barbershops/" + shop)
                        .header("Authorization", serviceBearer("barber-saas-platform-admin-api")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeBarbers").value(2));
    }

    @Test
    void an_unlimited_plan_never_refuses() throws Exception {
        UUID shop = barbershopOn(platformPlan("Premium", 999));

        for (int i = 0; i < 3; i++) {
            addBarber(shop).andExpect(status().isCreated());
        }
    }

    @Test
    void platform_admin_down_answers_503_and_creates_nothing() throws Exception {
        UUID shop = barbershopOn(platformPlanFailing());

        addBarber(shop).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
        http.perform(get("/api/v1/barbershops/me").header("Authorization", bearer("ADMIN_BARBERSHOP", shop)))
                .andExpect(jsonPath("$.activeBarbers").value(0));
    }
}
