package com.julianas.stockflow.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class InventoryConfirmationRepository {
    private final JdbcTemplate jdbc;

    public InventoryConfirmationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean tryLock(UUID key) {
        // Separate two-int namespace from administrator bootstrap and sale locks.
        // Hash collisions only cause a retry; the full UUID identifies the stored result.
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select pg_try_advisory_xact_lock(1937006968, ?)", Boolean.class, key.hashCode()));
    }

    public Optional<Confirmation> find(UUID key) {
        return jdbc.query("select request_hash, movement_id from inventory_confirmations where idempotency_key = ?",
                (row, number) -> new Confirmation(row.getString("request_hash"), row.getLong("movement_id")), key)
                .stream().findFirst();
    }

    public void save(UUID key, String hash, Long movementId) {
        jdbc.update("insert into inventory_confirmations (idempotency_key, request_hash, movement_id) values (?, ?, ?)",
                key, hash, movementId);
    }

    public record Confirmation(String requestHash, Long movementId) {}
}
