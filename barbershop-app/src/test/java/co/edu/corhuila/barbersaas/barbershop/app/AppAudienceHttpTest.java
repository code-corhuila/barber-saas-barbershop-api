package co.edu.corhuila.barbersaas.barbershop.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * barber-saas-docs#164: the routes of the app take only tokens issued for the app. A token signed by
 * identity-auth with a valid iss but without {@code aud: barber-saas-app} (copied from a log, or minted
 * for another service) answers 401 (authentication.md, validation step 4).
 */
class AppAudienceHttpTest extends HttpTest {

    private final UUID shop = UUID.randomUUID();

    @Test
    void a_token_without_the_app_audience_answers_401_on_patch_barbershops_me() throws Exception {
        for (String aud : Arrays.asList(null, "barber-saas-loyalty-api", "barber-saas-barbershop-api")) {
            http.perform(patch("/api/v1/barbershops/me").header("Authorization",
                                    bearerWithAudience(aud, "ADMIN_BARBERSHOP", shop))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otra\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
            http.perform(get("/api/v1/services").header("Authorization", bearerWithAudience(aud, "BARBER", shop)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void the_same_token_with_the_app_audience_reaches_the_operation() throws Exception {
        http.perform(get("/api/v1/services").header("Authorization",
                        bearerWithAudience(APP_AUDIENCE, "ADMIN_BARBERSHOP", shop)))
                .andExpect(status().isOk());
    }
}
