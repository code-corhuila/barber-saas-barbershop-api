package co.edu.corhuila.barbersaas.barbershop.adapter.out.http;

import co.edu.corhuila.barbersaas.barbershop.adapter.in.http.CorrelationFilter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;
import org.slf4j.MDC;

/**
 * A GET to another service's internal route with this service's own token, never a user's
 * (authentication.md, "Internal operations"), carrying the correlation id. Explicit limits (norm 5.3.10):
 * 2 s to connect, 3 s per request, no retries: the owner can send the request again.
 */
final class InternalHttp {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final String service;
    private final String baseUrl;
    private final String serviceToken;
    private final Function<String, RuntimeException> unavailable;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

    /** {@code unavailable} builds the port's own 503 exception from a reason that goes to the log only. */
    InternalHttp(String service, String baseUrl, String serviceToken, Function<String, RuntimeException> unavailable) {
        String url = baseUrl == null ? "" : baseUrl.strip();
        this.service = service;
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.serviceToken = serviceToken == null ? "" : serviceToken.strip();
        this.unavailable = unavailable;
    }

    /** The answer, whatever its status; unavailable when unset, unreachable, too slow or interrupted. */
    HttpResponse<byte[]> get(String path, String settings) {
        if (baseUrl.isEmpty() || serviceToken.isEmpty()) {
            throw unavailable.apply(settings + " or SERVICE_TOKEN is not set");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + serviceToken)
                .GET();
        String correlationId = MDC.get(CorrelationFilter.MDC_KEY);
        if (correlationId != null) {
            request.header("X-Correlation-Id", correlationId);
        }
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw unavailable.apply(service + " unreachable or too slow (" + e.getClass().getSimpleName() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable.apply(service + " call interrupted");
        }
    }
}
