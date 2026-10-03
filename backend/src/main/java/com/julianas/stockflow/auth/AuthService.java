package com.julianas.stockflow.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Isolation;

@Service
@ConditionalOnProperty(name = "app.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void createInitialAdministrator() {
        users.lockBootstrap();
        if (users.count() != 0) {
            LOGGER.debug("Administrator bootstrap skipped: an account already exists");
            return;
        }
        String email = required(properties.admin().email(), "APP_ADMIN_EMAIL").toLowerCase(Locale.ROOT);
        String password = required(properties.admin().password(), "APP_ADMIN_PASSWORD");
        users.saveAndFlush(new ApplicationUser(email, passwordEncoder.encode(password), Instant.now(clock)));
        LOGGER.info("Initial administrator inserted; transaction will commit before startup completes");
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
