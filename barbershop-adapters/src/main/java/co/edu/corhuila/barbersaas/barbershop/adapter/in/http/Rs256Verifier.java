package co.edu.corhuila.barbersaas.barbershop.adapter.in.http;

import co.edu.corhuila.barbersaas.barbershop.application.port.in.Caller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Each service validates the token itself (authentication.md, norm 5.3.7). The gateway only filters
 * requests that carry no credentials; a service that trusted a header set upstream would accept
 * anyone who reaches it through the internal network. Same rules as identity-auth-api's verifier,
 * plus the issuer and the claims this service needs: role and barbershopId.
 */
public class Rs256Verifier {

    public static class InvalidTokenException extends Exception {
        InvalidTokenException(String message) {
            super(message);
        }
    }

    public static final String ISSUER = "barber-saas-identity-auth-api";
    /** The audience of every user token (identity-auth-api #39); service tokens are not checked by it. */
    public static final String APP_AUDIENCE = "barber-saas-app";
    private static final long LEEWAY_SECONDS = 30;

    private final PublicKey key;
    private final ObjectMapper json = new ObjectMapper();

    public Rs256Verifier(String publicKeyPem) {
        if (publicKeyPem == null || !publicKeyPem.contains("BEGIN PUBLIC KEY")) {
            throw new IllegalArgumentException("JWT public key is not a PEM public key");
        }
        String base64 = publicKeyPem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        try {
            key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("JWT public key cannot be read", e);
        }
    }

    /** Returns who calls: subject, role and tenant, exactly as the token states them. Used on /internal/. */
    public Caller verify(String token, Instant now) throws InvalidTokenException {
        return verify(token, now, false);
    }

    /**
     * The same, for the routes of the app (authentication.md, validation step 4, docs#164): the token
     * must also name {@link #APP_AUDIENCE} in {@code aud}, as every user token of identity-auth does.
     */
    public Caller verifyForApp(String token, Instant now) throws InvalidTokenException {
        return verify(token, now, true);
    }

    /** {@code aud} is a string or an array of strings (RFC 7519, 4.1.3). */
    private static boolean names(JsonNode aud, String audience) {
        if (aud.isArray()) {
            for (JsonNode one : aud) {
                if (one.isTextual() && audience.equals(one.asText())) {
                    return true;
                }
            }
            return false;
        }
        return aud.isTextual() && audience.equals(aud.asText());
    }

    private Caller verify(String token, Instant now, boolean forApp) throws InvalidTokenException {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("malformed token");
        }
        try {
            JsonNode header = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            // A closed rule: a token that declares "none" or HS256 is rejected
            // instead of being checked with the wrong algorithm.
            if (!"RS256".equals(header.path("alg").asText())) {
                throw new InvalidTokenException("algorithm not allowed");
            }
            Signature rsa = Signature.getInstance("SHA256withRSA");
            rsa.initVerify(key);
            rsa.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!rsa.verify(Base64.getUrlDecoder().decode(parts[2]))) {
                throw new InvalidTokenException("bad signature");
            }
            JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!claims.path("exp").isNumber() || !claims.path("sub").isTextual()) {
                throw new InvalidTokenException("exp and sub are required");
            }
            if (now.getEpochSecond() > claims.get("exp").asLong() + LEEWAY_SECONDS) {
                throw new InvalidTokenException("expired");
            }
            if (!ISSUER.equals(claims.path("iss").asText())) {
                throw new InvalidTokenException("unknown issuer");
            }
            if (forApp && !names(claims.path("aud"), APP_AUDIENCE)) {
                throw new InvalidTokenException("not issued for the app");
            }
            Caller.Role role = Caller.Role.valueOf(claims.path("role").asText());
            UUID tenant = claims.path("barbershopId").isTextual()
                    ? UUID.fromString(claims.get("barbershopId").asText()) : null;
            return new Caller(claims.get("sub").asText(), role, tenant);
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("unreadable token");
        }
    }
}
