package co.edu.corhuila.barbersaas.barbershop.adapter.in.http;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the bearer token on every route under /api/ except the anonymous discovery catalog, and on
 * every route under /internal/ (authentication.md, "Internal operations": never anonymous). The routes
 * under /api/ take only user tokens; service tokens belong to /internal/.
 */
public class AuthFilter extends OncePerRequestFilter {

    public static final String CALLER_ATTRIBUTE = "auth.caller";

    /** DEC-SHOP-02: GET /api/v1/barbershops, /{id}, /{id}/services and /{id}/barbers. "/me" is not an id. */
    private static final Pattern PUBLIC = Pattern.compile(
            "^/api/v1/barbershops(/[0-9a-fA-F-]{36}(/services|/barbers)?)?/?$");

    private final Rs256Verifier verifier;
    private final ObjectMapper json;

    public AuthFilter(Rs256Verifier verifier, ObjectMapper json) {
        this.verifier = verifier;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/internal/")) {
            return false;
        }
        if (!path.startsWith("/api/")) {
            return true;
        }
        return "GET".equals(request.getMethod()) && PUBLIC.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() == 7) {
            reject(response, "a bearer token is required");
            return;
        }
        Caller caller;
        try {
            caller = verifier.verify(header.substring(7), Instant.now());
        } catch (Rs256Verifier.InvalidTokenException e) {
            reject(response, "the token is invalid or has expired");
            return;
        }
        // barber-saas-docs#164: service tokens are only for /internal/v1/**; the routes of the app take
        // user tokens, so a copied service token cannot act as an owner.
        if (caller.is(Caller.Role.SERVICE) && request.getRequestURI().startsWith("/api/")) {
            reject(response, "service tokens are only accepted on internal routes");
            return;
        }
        request.setAttribute(CALLER_ATTRIBUTE, caller);
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ApiError.of(ApiError.UNAUTHORIZED, message));
    }
}
