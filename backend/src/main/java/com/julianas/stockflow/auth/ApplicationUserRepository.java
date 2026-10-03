package com.julianas.stockflow.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ApplicationUserRepository extends JpaRepository<ApplicationUser, Long> {

    // A separate two-int PostgreSQL advisory-lock namespace from sale UUID locks.
    // Transaction-scoped and shared by all nodes, even with different bootstrap emails.
    @Query(value = "select 1 from pg_advisory_xact_lock(1937006967, 1)", nativeQuery = true)
    int lockBootstrap();

    Optional<ApplicationUser> findByEmailIgnoreCase(String email);
}
