package co.edu.corhuila.barbersaas.barbershop.adapter.out.http;

import co.edu.corhuila.barbersaas.barbershop.application.port.out.Users;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * GET /internal/v1/users/{id} of identity-auth-api (auth-service.yaml getInternalUser) with this
 * service's own token, never a user's (authentication.md, "Internal operations"). Limits: InternalHttp.
 */
public class HttpUsers implements Users {

    private final InternalHttp http;
    private final ObjectMapper json = new ObjectMapper();

    public HttpUsers(String baseUrl, String serviceToken) {
        this.http = new InternalHttp("identity-auth-api", baseUrl, serviceToken, Unavailable::new);
    }

    @Override
    public Optional<User> find(UUID id) {
        HttpResponse<byte[]> response = http.get("/internal/v1/users/" + id, "IDENTITY_AUTH_API_URL");
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            throw new Unavailable("identity-auth-api answered " + response.statusCode() + " to the user read");
        }
        return Optional.of(read(response.body()));
    }

    private User read(byte[] body) {
        try {
            JsonNode u = json.readTree(body);
            JsonNode shop = u.path("barbershopId");
            return new User(UUID.fromString(u.path("id").asText()), text(u.path("fullName")),
                    text(u.path("profilePhotoUrl")), u.path("role").asText(),
                    shop.isTextual() ? UUID.fromString(shop.asText()) : null, u.path("isActive").asBoolean(false));
        } catch (IOException | IllegalArgumentException e) {
            throw new Unavailable("identity-auth-api answered a user that cannot be read");
        }
    }

    private static String text(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }
}
