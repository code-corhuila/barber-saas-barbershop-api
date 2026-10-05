package co.edu.corhuila.barbersaas.barbershop.app;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP contract with the in-memory repositories (DATABASE_URL empty): the whole service starts,
 * and tokens are signed with a throwaway key pair, exactly as identity-auth-api signs them. A local
 * server stands in for identity-auth's GET /internal/v1/users/{id}: it knows the users registered here.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class HttpTest {

    static final KeyPair KEYS = generate();
    static final String SERVICE_TOKEN = "barbershop-service-token";
    /** The JSON identity-auth answers per user id; FAILING makes it answer 500 for that id. */
    private static final Map<UUID, String> IDENTITY_USERS = new ConcurrentHashMap<>();
    private static final String FAILING = "";
    private static final HttpServer IDENTITY = identityAuth();

    @Autowired
    MockMvc http;

    @DynamicPropertySource
    static void keys(DynamicPropertyRegistry registry) {
        registry.add("JWT_PUBLIC_KEY", () -> "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(KEYS.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----");
        registry.add("IDENTITY_AUTH_API_URL", () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort());
        registry.add("SERVICE_TOKEN", () -> SERVICE_TOKEN);
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 403 without this service's token, 404 for an unknown user, 500 for one registered as failing. */
    private static HttpServer identityAuth() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/v1/users/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String user = IDENTITY_USERS.get(UUID.fromString(path.substring(path.lastIndexOf('/') + 1)));
                boolean ours = ("Bearer " + SERVICE_TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"));
                int status = !ours ? 403 : user == null ? 404 : user.equals(FAILING) ? 500 : 200;
                byte[] body = status == 200 ? user.getBytes(StandardCharsets.UTF_8) : new byte[0];
                exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
                if (status == 200) {
                    exchange.getResponseBody().write(body);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A user identity-auth knows, as GET /internal/v1/users/{id} returns it. */
    static UUID identityUser(String fullName, String role, UUID barbershopId, boolean active) {
        UUID id = UUID.randomUUID();
        IDENTITY_USERS.put(id, "{\"id\":\"" + id + "\",\"fullName\":\"" + fullName + "\",\"profilePhotoUrl\":null,"
                + "\"role\":\"" + role + "\",\"barbershopId\":"
                + (barbershopId == null ? "null" : "\"" + barbershopId + "\"") + ",\"isActive\":" + active + "}");
        return id;
    }

    static UUID identityBarber(String fullName, UUID barbershopId) {
        return identityUser(fullName, "BARBER", barbershopId, true);
    }

    /** A user whose read makes identity-auth answer 500. */
    static UUID identityFailing() {
        UUID id = UUID.randomUUID();
        IDENTITY_USERS.put(id, FAILING);
        return id;
    }

    /** "Bearer ..." for a user with that role; {@code barbershopId} null for CLIENT and SUPER_ADMIN. */
    static String bearer(UUID userId, String role, UUID barbershopId) {
        return signed(userId.toString(), role, barbershopId);
    }

    /** "Bearer ..." for a service token: role SERVICE, the service name as sub, no barbershop. */
    static String serviceBearer(String service) {
        return signed(service, "SERVICE", null);
    }

    private static String signed(String subject, String role, UUID barbershopId) {
        long now = Instant.now().getEpochSecond();
        String claims = "{\"iss\":\"barber-saas-identity-auth-api\",\"sub\":\"" + subject + "\",\"role\":\"" + role + "\""
                + (barbershopId == null ? "" : ",\"barbershopId\":\"" + barbershopId + "\"")
                + ",\"iat\":" + now + ",\"exp\":" + (now + 600) + "}";
        try {
            String input = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"dev-1\"}") + "." + b64(claims);
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initSign(KEYS.getPrivate());
            rsa.update(input.getBytes(StandardCharsets.US_ASCII));
            return "Bearer " + input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(rsa.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String bearer(String role, UUID barbershopId) {
        return bearer(UUID.randomUUID(), role, barbershopId);
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
