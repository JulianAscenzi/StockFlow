package com.julianas.stockflow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(Admin admin, Jwt jwt) {

    public record Admin(String email, String password) {
    }

    public record Jwt(String secret, Duration expiration) {
    }
}
