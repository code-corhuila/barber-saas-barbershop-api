package co.edu.corhuila.barbersaas.barbershop.app;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.barbersaas.barbershop.adapter.out.persistence.InMemoryBarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.BarbershopRepository;
import co.edu.corhuila.barbersaas.barbershop.domain.model.Barbershop;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** barbershop-service.yaml, tags Barbershops and Services, over HTTP with the in-memory repositories. */
class CatalogHttpTest extends HttpTest {

    private static final String CUT = "{\"name\":\"Classic haircut\",\"durationMinutes\":30,\"priceCents\":2500000}";

    @Autowired
    BarbershopRepository barbershops;

    private UUID shop;
    private UUID other;

    /** Barbershops are created by platform-admin (OQ-10); the test seeds them in the in-memory repository. */
    @BeforeEach
    void seed() {
        shop = add("Neiva-" + UUID.randomUUID());
        other = add("Neiva-" + UUID.randomUUID());
    }

    private UUID add(String city) {
        Barbershop b = Barbershop.register(UUID.randomUUID(), "El Clásico", city, Instant.now());
        ((InMemoryBarbershopRepository) barbershops).put(b);
        return b.id();
    }

    private String createService(UUID barbershop, String key) throws Exception {
        String body = http.perform(post("/api/v1/services").header("Authorization", bearer("ADMIN_BARBERSHOP", barbershop))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(CUT))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void anyone_searches_visible_barbershops_by_city_with_the_page_envelope() throws Exception {
        Barbershop b = barbershops.findById(shop).orElseThrow();

        http.perform(get("/api/v1/barbershops").param("city", b.city().toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(shop.toString()))
                .andExpect(jsonPath("$.data[0].status").value("TRIAL"))
                .andExpect(jsonPath("$.data[0].address").value(nullValue()))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(1));
    }

    @Test
    void the_search_rejects_half_coordinates_and_bad_paging() throws Exception {
        http.perform(get("/api/v1/barbershops").param("lat", "2.9")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        http.perform(get("/api/v1/barbershops").param("limit", "101")).andExpect(status().isBadRequest());
        // Only a UUID is part of the public catalog: anything else needs a token, and then fails its shape.
        http.perform(get("/api/v1/barbershops/not-a-uuid")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/barbershops/not-a-uuid").header("Authorization", bearer("CLIENT", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void the_public_catalog_shows_active_services_and_hides_unknown_barbershops() throws Exception {
        createService(shop, "key-" + UUID.randomUUID());

        http.perform(get("/api/v1/barbershops/" + shop + "/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].priceCents").value(2500000))
                .andExpect(jsonPath("$.data[0].isActive").value(true));
        http.perform(get("/api/v1/barbershops/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void the_owner_creates_a_service_and_a_retry_answers_200_with_the_same_one() throws Exception {
        String key = "key-" + UUID.randomUUID();
        String id = createService(shop, key);

        http.perform(post("/api/v1/services").header("Authorization", bearer("ADMIN_BARBERSHOP", shop))
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(CUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void a_creation_needs_a_valid_key_and_body_and_rejects_unknown_fields() throws Exception {
        String owner = bearer("ADMIN_BARBERSHOP", shop);
        http.perform(post("/api/v1/services").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(CUT)).andExpect(status().isBadRequest());
        http.perform(post("/api/v1/services").header("Authorization", owner).header("Idempotency-Key", "key-00000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cut\",\"durationMinutes\":30,\"priceCents\":1,\"barbershopId\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("barbershopId"));
        http.perform(post("/api/v1/services").header("Authorization", owner).header("Idempotency-Key", "key-00000002")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cut\",\"durationMinutes\":4,\"priceCents\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("durationMinutes"));
        http.perform(post("/api/v1/services").header("Authorization", bearer("BARBER", shop))
                        .header("Idempotency-Key", "key-00000003").contentType(MediaType.APPLICATION_JSON).content(CUT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void another_barbershop_never_sees_or_edits_the_service() throws Exception {
        String id = createService(shop, "key-" + UUID.randomUUID());
        String stranger = bearer("ADMIN_BARBERSHOP", other);

        http.perform(get("/api/v1/services/" + id).header("Authorization", stranger)).andExpect(status().isNotFound());
        http.perform(put("/api/v1/services/" + id).header("Authorization", stranger)
                .contentType(MediaType.APPLICATION_JSON).content(CUT)).andExpect(status().isNotFound());
        http.perform(get("/api/v1/services").header("Authorization", stranger))
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void a_deactivated_service_is_hidden_from_barbers() throws Exception {
        String id = createService(shop, "key-" + UUID.randomUUID());

        http.perform(put("/api/v1/services/" + id).header("Authorization", bearer("ADMIN_BARBERSHOP", shop))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Classic haircut\",\"durationMinutes\":30,\"priceCents\":2600000,\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
        http.perform(get("/api/v1/services/" + id).header("Authorization", bearer("BARBER", shop)))
                .andExpect(status().isNotFound());
    }

    @Test
    void the_owner_edits_my_barbershop_but_never_its_status() throws Exception {
        String owner = bearer("ADMIN_BARBERSHOP", shop);

        http.perform(patch("/api/v1/barbershops/me").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cancellationPolicyHours\":4,\"address\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationPolicyHours").value(4));
        http.perform(patch("/api/v1/barbershops/me").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isBadRequest());
        http.perform(patch("/api/v1/barbershops/me").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/barbershops/me").header("Authorization", bearer("SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
        http.perform(get("/api/v1/barbershops/me").header("Authorization", bearer("BARBER", shop)))
                .andExpect(jsonPath("$.id").value(shop.toString()))
                .andExpect(header().exists("X-Correlation-Id"));
    }
}
