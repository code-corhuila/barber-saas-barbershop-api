package co.edu.corhuila.barbersaas.barbershop.app;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** barbershop-service.yaml, tag Internal (DEC-SHOP-05): only the workflow's service token. */
class InternalBarbershopHttpTest extends HttpTest {

    private static final String PATH = "/internal/v1/barbershops";
    private static final String BODY = "{\"name\":\"El Clásico\",\"city\":\"Neiva\",\"address\":\"Calle 5 # 10-20\","
            + "\"phone\":\"+573001112233\",\"latitude\":2.9273,\"longitude\":-75.2819}";

    private final String workflow = serviceBearer("barber-saas-workflow");

    private ResultActions create(String token, String key, String body) throws Exception {
        var request = post(PATH).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", token);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return http.perform(request);
    }

    private String createdId() throws Exception {
        String body = create(workflow, "signup-" + UUID.randomUUID(), BODY)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void the_workflow_creates_a_barbershop_on_trial_and_a_retry_returns_it_with_200() throws Exception {
        String key = "signup-" + UUID.randomUUID();

        String body = create(workflow, key, BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.planId").doesNotExist())
                .andExpect(jsonPath("$.address").value("Calle 5 # 10-20"))
                .andReturn().getResponse().getContentAsString();
        Instant createdAt = Instant.parse(JsonPath.read(body, "$.createdAt"));
        assertEquals(createdAt.plus(Duration.ofDays(60)), Instant.parse(JsonPath.read(body, "$.trialEndsAt")));

        create(workflow, key, BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(JsonPath.<String>read(body, "$.id")));
    }

    @Test
    void every_shape_error_is_reported_field_by_field_with_400() throws Exception {
        create(workflow, "signup-" + UUID.randomUUID(), "{\"name\":\"\",\"latitude\":91,\"status\":\"ACTIVE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field").value(hasItems("name", "city", "latitude", "status")));
        create(workflow, null, BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("Idempotency-Key"));
    }

    @Test
    void without_a_token_the_internal_operations_answer_401() throws Exception {
        create(null, "signup-" + UUID.randomUUID(), BODY)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
        http.perform(delete(PATH + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void a_user_token_answers_403() throws Exception {
        String owner = bearer("ADMIN_BARBERSHOP", UUID.randomUUID());

        create(owner, "signup-" + UUID.randomUUID(), BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
        http.perform(delete(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer("SUPER_ADMIN", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void a_service_token_of_another_service_answers_403() throws Exception {
        String worker = serviceBearer("barber-saas-worker");

        create(worker, "signup-" + UUID.randomUUID(), BODY).andExpect(status().isForbidden());
        http.perform(delete(PATH + "/" + createdId()).header("Authorization", worker))
                .andExpect(status().isForbidden());
    }

    @Test
    void the_compensation_answers_204_twice() throws Exception {
        String id = createdId();

        http.perform(delete(PATH + "/" + id).header("Authorization", workflow)).andExpect(status().isNoContent());
        http.perform(delete(PATH + "/" + id).header("Authorization", workflow)).andExpect(status().isNoContent());
    }

    @Test
    void a_barbershop_with_barbers_is_not_removed() throws Exception {
        String id = createdId();
        http.perform(post("/api/v1/barbers").header("Authorization", bearer("ADMIN_BARBERSHOP", UUID.fromString(id)))
                        .header("Idempotency-Key", "key-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + identityBarber("Juan", UUID.fromString(id)) + "\"}"))
                .andExpect(status().isCreated());

        http.perform(delete(PATH + "/" + id).header("Authorization", workflow))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }
}
