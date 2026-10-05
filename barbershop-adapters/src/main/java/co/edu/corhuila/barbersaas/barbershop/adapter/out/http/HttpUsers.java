package co.edu.corhuila.barbersaas.barbershop.adapter.out.http;

import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.CorrelationFilter;
import co.edu.corhuila.barbersaas.barbershop.application.port.out.Users;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * GET /internal/v1/users/{id} of identity-auth-api (auth-service.yaml getInternalUser) with this
 * service's own token, never a user's (authentication.md, "Internal operations"). Explicit limits
 * (norm 5.3.10): 2 s to connect, 3 s per request, no retries: the owner can save the profile again.
 */
public class HttpUsers implements Users {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;
    private final String serviceToken;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();

    public HttpUsers(String baseUrl, String serviceToken) {
        String url = baseUrl == null ? "" : baseUrl.strip();
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.serviceToken = serviceToken == null ? "" : serviceToken.strip();
    }

    @Override
    public Optional<User> find(UUID id) {
        if (baseUrl.isEmpty() || serviceToken.isEmpty()) {
            throw new Unavailable("IDENTITY_AUTH_API_URL or SERVICE_TOKEN is not set");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/internal/v1/users/" + id))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + serviceToken)
                .GET();
        String correlationId = MDC.get(CorrelationFilter.MDC_KEY);
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        HttpResponse<byte[]> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new Unavailable("identity-auth-api unreachable or too slow (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("identity-auth-api call interrupted");
        }
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
