package com.julianas.stockflow.sale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SaleConfirmationRepository {
    private final JdbcTemplate jdbc;

    public SaleConfirmationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Transaction-scoped: released by PostgreSQL on either commit or rollback.
    public boolean tryLock(UUID key) {
        long lockId = key.getMostSignificantBits() ^ key.getLeastSignificantBits();
        return Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, lockId));
    }

    public Optional<Confirmation> find(UUID key) {
        return jdbc.query("select request_hash, sale_id from sale_confirmations where idempotency_key = ?",
                (row, number) -> new Confirmation(row.getString("request_hash"), row.getLong("sale_id")), key)
                .stream().findFirst();
    }

    public void save(UUID key, String hash, Long saleId) {
        jdbc.update("insert into sale_confirmations (idempotency_key, request_hash, sale_id) values (?, ?, ?)",
                key, hash, saleId);
    }

    public record Confirmation(String requestHash, Long saleId) {}
}
