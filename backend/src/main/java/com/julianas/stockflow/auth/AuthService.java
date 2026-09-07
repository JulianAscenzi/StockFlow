package com.julianas.stockflow.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final ApplicationUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthService(
            ApplicationUserRepository users,
            PasswordEncoder passwordEncoder,
            AuthProperties properties,
            Clock clock
    ) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void createInitialAdministrator() {
        if (users.count() != 0) {
            return;
        }
        String email = required(properties.admin().email(), "APP_ADMIN_EMAIL").toLowerCase(Locale.ROOT);
        String password = required(properties.admin().password(), "APP_ADMIN_PASSWORD");
        users.save(new ApplicationUser(email, passwordEncoder.encode(password), Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public String authenticate(String email, String password) {
        ApplicationUser user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return user.getEmail();
    }

    private String required(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(environmentVariable + " must be configured");
        }
        return value;
    }
}
