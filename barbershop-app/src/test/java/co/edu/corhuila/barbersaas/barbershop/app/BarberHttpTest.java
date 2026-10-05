package co.edu.corhuila.barbersaas.barbershop.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** barbershop-service.yaml, tag Barbers, over HTTP with the in-memory repositories. */
class BarberHttpTest extends HttpTest {

    private final UUID shop = UUID.randomUUID();
    private final UUID barberUser = identityBarber("Juan Perez", shop);
    private final String owner = bearer("ADMIN_BARBERSHOP", shop);
    private final String barber = bearer(barberUser, "BARBER", shop);

    private String createProfile() throws Exception {
        String body = http.perform(post("/api/v1/barbers").header("Authorization", owner)
                        .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + barberUser + "\",\"experienceYears\":3,\"bio\":\"Fades\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.fullName").value("Juan Perez"))
                .andExpect(jsonPath("$.ratingAvg").value(0))
                .andExpect(jsonPath("$.specialties").isEmpty())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void the_owner_creates_one_profile_per_user() throws Exception {
        createProfile();

        http.perform(post("/api/v1/barbers").header("Authorization", owner)
                        .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + barberUser + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("The user already has a barber profile"));
        http.perform(post("/api/v1/barbers").header("Authorization", owner)
                        .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions createFor(UUID user) throws Exception {
        return http.perform(post("/api/v1/barbers").header("Authorization", owner)
                .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"" + user + "\"}"));
    }

    @Test
    void the_user_is_checked_against_identity_auth_before_the_profile_is_created() throws Exception {
        createFor(UUID.randomUUID()).andExpect(status().isNotFound());
        createFor(identityBarber("Ana", UUID.randomUUID())).andExpect(status().isNotFound());
        createFor(identityUser("Luis", "CLIENT", shop, true)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
        createFor(identityUser("Eva", "BARBER", shop, false)).andExpect(status().isUnprocessableEntity());
        createFor(identityFailing()).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));

        http.perform(get("/api/v1/barbers").header("Authorization", owner)).andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void the_name_is_in_the_owner_list_and_in_the_public_catalog() throws Exception {
        String created = http.perform(post("/internal/v1/barbershops")
                        .header("Authorization", serviceBearer("barber-saas-workflow"))
                        .header("Idempotency-Key", "signup-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"El Clasico\",\"city\":\"Neiva\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID openShop = UUID.fromString(JsonPath.read(created, "$.id"));
        UUID user = identityBarber("Carlos Leal", openShop);
        http.perform(post("/api/v1/barbers").header("Authorization", bearer("ADMIN_BARBERSHOP", openShop))
                        .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + user + "\"}"))
                .andExpect(status().isCreated());

        http.perform(get("/api/v1/barbers").header("Authorization", bearer("CLIENT", openShop)))
                .andExpect(jsonPath("$.data[0].fullName").value("Carlos Leal"))
                .andExpect(jsonPath("$.data[0].profilePhotoUrl").isEmpty());
        http.perform(get("/api/v1/barbershops/" + openShop + "/barbers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fullName").value("Carlos Leal"));
    }

    @Test
    void a_barber_edits_their_own_profile_but_never_the_rating() throws Exception {
        String id = createProfile();

        http.perform(patch("/api/v1/barbers/" + id).header("Authorization", barber)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"experienceYears\":5,\"bio\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experienceYears").value(5))
                .andExpect(jsonPath("$.bio").doesNotExist());
        http.perform(patch("/api/v1/barbers/" + id).header("Authorization", barber)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ratingAvg\":5}")).andExpect(status().isBadRequest());
        http.perform(patch("/api/v1/barbers/" + id).header("Authorization", bearer("BARBER", shop))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"experienceYears\":9}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void specialties_are_listed_filtered_and_removed() throws Exception {
        String id = createProfile();

        String body = http.perform(post("/api/v1/barbers/" + id + "/specialties").header("Authorization", barber)
                        .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specialtyName\":\"Fade\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String specialty = JsonPath.read(body, "$.id");

        http.perform(get("/api/v1/barbers").param("specialty", "Fade").header("Authorization", bearer("CLIENT", shop)))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].specialties[0].specialtyName").value("Fade"));
        http.perform(get("/api/v1/barbers/" + id + "/specialties").header("Authorization", owner))
                .andExpect(jsonPath("$.data[0].id").value(specialty));
        http.perform(delete("/api/v1/barbers/" + id + "/specialties/" + specialty).header("Authorization", barber))
                .andExpect(status().isNoContent());
        http.perform(delete("/api/v1/barbers/" + id + "/specialties/" + specialty).header("Authorization", barber))
                .andExpect(status().isNotFound());
    }

    @Test
    void a_profile_of_another_barbershop_is_not_found() throws Exception {
        String id = createProfile();
        String stranger = bearer("ADMIN_BARBERSHOP", UUID.randomUUID());

        http.perform(get("/api/v1/barbers/" + id).header("Authorization", stranger)).andExpect(status().isNotFound());
        http.perform(get("/api/v1/barbers/" + id + "/specialties").header("Authorization", stranger))
                .andExpect(status().isNotFound());
        http.perform(get("/api/v1/barbers").header("Authorization", stranger)).andExpect(jsonPath("$.meta.total").value(0));
    }
}
