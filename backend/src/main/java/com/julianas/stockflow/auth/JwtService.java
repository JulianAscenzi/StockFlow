package com.julianas.stockflow.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HMAC_SHA_256 = "HmacSHA256";

    private final byte[] secret;
    private final AuthProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public JwtService(AuthProperties properties, Clock clock, ObjectMapper objectMapper) {
        String configuredSecret = properties.jwt().secret();
        if (configuredSecret == null || configuredSecret.length() < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 characters");
        }
        this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        this.properties = properties;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public String issue(String email) {
        Instant now = Instant.now(clock);
        String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encodeJson(Map.of(
                "sub", email,
                "iat", now.getEpochSecond(),
                "exp", now.plus(properties.jwt().expiration()).getEpochSecond()
        ));
        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + ENCODER.encodeToString(sign(unsignedToken));
    }

    public Optional<String> subject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), decode(parts[2]))) {
            return Optional.empty();
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(decode(parts[1]), Map.class);
            Object subject = payload.get("sub");
            Object expiresAt = payload.get("exp");
            if (!(subject instanceof String email) || email.isBlank() || !(expiresAt instanceof Number expiration)
                    || Instant.now(clock).getEpochSecond() >= expiration.longValue()) {
                return Optional.empty();
            }
            return Optional.of(email);
        } catch (JacksonException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String encodeJson(Map<String, Object> values) {
        try {
            return ENCODER.encodeToString(objectMapper.writeValueAsBytes(values));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to create authentication token", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA_256));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign authentication token", exception);
        }
    }

    private byte[] decode(String value) {
        try {
            return DECODER.decode(value);
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }

}
