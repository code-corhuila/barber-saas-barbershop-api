package co.edu.corhuila.barbersaas.barbershop.adapter.out.http;

import co.edu.corhuila.barbersaas.barbershop.application.port.out.Plans;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * GET /internal/v1/plans/{id} of platform-admin-api (platform-admin-service.yaml getPlanInternal,
 * DEC-SHOP-07) with this service's own token. Limits: InternalHttp.
 */
public class HttpPlans implements Plans {

    private final InternalHttp http;
    private final ObjectMapper json = new ObjectMapper();

    public HttpPlans(String baseUrl, String serviceToken) {
        this.http = new InternalHttp("platform-admin-api", baseUrl, serviceToken, Unavailable::new);
    }

    @Override
    public Optional<Plan> find(UUID id) {
        HttpResponse<byte[]> response = http.get("/internal/v1/plans/" + id, "PLATFORM_ADMIN_API_URL");
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new Unavailable("platform-admin-api answered " + response.statusCode() + " to the plan read");
        }
        return Optional.of(read(response.body()));
    }

    private Plan read(byte[] body) {
        try {
            JsonNode p = json.readTree(body);
            if (!p.path("maxBarbers").canConvertToInt() || !p.path("maxBarbers").isIntegralNumber()) {
                throw new IllegalArgumentException("maxBarbers");
            }
            return new Plan(UUID.fromString(p.path("id").asText()), p.path("name").asText(),
                    p.path("maxBarbers").asInt());
        } catch (IOException | IllegalArgumentException e) {
            throw new Unavailable("platform-admin-api answered a plan that cannot be read");
        }
    }
}
