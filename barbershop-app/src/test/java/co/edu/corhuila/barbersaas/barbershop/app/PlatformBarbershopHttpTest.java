package co.edu.corhuila.barbersaas.barbershop.app;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** barbershop-service.yaml 1.4.0, DEC-SHOP-06: platform-admin operates any barbershop with its service token. */
class PlatformBarbershopHttpTest extends HttpTest {

    private static final String PATH = "/internal/v1/barbershops";

    private final String platform = serviceBearer("barber-saas-platform-admin-api");

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return http.perform(request.header("Authorization", token));
    }

    /** platform-admin onboards a barbershop by hand: the internal creation accepts its token too. */
    private String onboarded() throws Exception {
        String body = as(platform, post(PATH).header("Idempotency-Key", "platform-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"La Esquina\",\"city\":\"Neiva\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions changeStatus(String token, String id, String status) throws Exception {
        return as(token, patch(PATH + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private ResultActions assignPlan(String token, String id, String plan) throws Exception {
        return as(token, put(PATH + "/" + id + "/plan").contentType(MediaType.APPLICATION_JSON)
                .content("{\"planId\":\"" + plan + "\"}"));
    }

    @Test
    void reads_any_barbershop_and_an_unknown_one_answers_404() throws Exception {
        String id = onboarded();

        as(platform, get(PATH + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.trialEndsAt").exists());
        as(platform, get(PATH + "/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        as(platform, get(PATH + "/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void walks_the_lifecycle_and_answers_200_for_the_status_it_already_has() throws Exception {
        String id = onboarded();

        changeStatus(platform, id, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        changeStatus(platform, id, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        changeStatus(platform, id, "SUSPENDED").andExpect(status().isOk());
        changeStatus(platform, id, "CANCELLED").andExpect(status().isOk());
    }

    @Test
    void a_transition_the_lifecycle_forbids_answers_409() throws Exception {
        String trial = onboarded();
        String cancelled = onboarded();
        changeStatus(platform, cancelled, "ACTIVE");
        changeStatus(platform, cancelled, "CANCELLED");

        changeStatus(platform, trial, "CANCELLED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_STATUS_TRANSITION"));
        changeStatus(platform, cancelled, "ACTIVE").andExpect(status().isConflict());
        as(platform, get(PATH + "/" + cancelled)).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void the_body_of_a_status_change_is_validated() throws Exception {
        String id = onboarded();

        changeStatus(platform, id, "TRIAL").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("status"));
        as(platform, patch(PATH + "/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\",\"planId\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("planId")));
    }

    @Test
    void assigns_the_plan_but_never_to_a_cancelled_barbershop() throws Exception {
        String id = onboarded();
        String plan = UUID.randomUUID().toString();

        assignPlan(platform, id, plan).andExpect(status().isOk()).andExpect(jsonPath("$.planId").value(plan));
        assignPlan(platform, id, "not-a-uuid").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("planId"));
        changeStatus(platform, id, "ACTIVE");
        changeStatus(platform, id, "CANCELLED");
        assignPlan(platform, id, UUID.randomUUID().toString()).andExpect(status().isConflict());
        assignPlan(platform, UUID.randomUUID().toString(), plan).andExpect(status().isNotFound());
    }

    @Test
    void lists_every_status_with_its_filters_in_pages() throws Exception {
        String plan = UUID.randomUUID().toString();
        String first = onboarded();
        String second = onboarded();
        assignPlan(platform, first, plan);
        assignPlan(platform, second, plan);
        changeStatus(platform, second, "SUSPENDED");

        as(platform, get(PATH).param("planId", plan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", is(java.util.List.of(second, first))))
                .andExpect(jsonPath("$.meta.total").value(2));
        as(platform, get(PATH).param("planId", plan).param("status", "TRIAL"))
                .andExpect(jsonPath("$.data[*].id", is(java.util.List.of(first))));
        as(platform, get(PATH).param("status", "SUSPENDED"))
                .andExpect(jsonPath("$.data[*].status", everyItem(is("SUSPENDED"))));
        as(platform, get(PATH).param("status", "TRIAL").param("trialEndsBefore", "2000-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.meta.total").value(0));
        as(platform, get(PATH).param("planId", plan).param("limit", "1"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void an_invalid_filter_answers_400_naming_it() throws Exception {
        as(platform, get(PATH).param("status", "OPEN")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("status"));
        as(platform, get(PATH).param("planId", "x")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("planId"));
        as(platform, get(PATH).param("trialEndsBefore", "yesterday")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("trialEndsBefore"));
        as(platform, get(PATH).param("limit", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void a_user_token_or_another_service_answers_403() throws Exception {
        String id = onboarded();
        String superAdmin = bearer("SUPER_ADMIN", null);
        String workflow = serviceBearer("barber-saas-workflow");

        for (String token : new String[] {superAdmin, workflow}) {
            as(token, get(PATH)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
            as(token, get(PATH + "/" + id)).andExpect(status().isForbidden());
            changeStatus(token, id, "ACTIVE").andExpect(status().isForbidden());
            assignPlan(token, id, UUID.randomUUID().toString()).andExpect(status().isForbidden());
        }
        as(platform, get(PATH + "/" + id)).andExpect(jsonPath("$.status", not("ACTIVE")));
    }

    @Test
    void without_a_token_they_answer_401() throws Exception {
        http.perform(get(PATH)).andExpect(status().isUnauthorized());
        http.perform(patch(PATH + "/" + UUID.randomUUID() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}")).andExpect(status().isUnauthorized());
    }
}
