package com.julianas.stockflow.inventory.api;

import com.julianas.stockflow.api.ApiIntegrationTestSupport;
import com.julianas.stockflow.category.CategoryService;
import com.julianas.stockflow.inventory.IdempotentInventoryService;
import com.julianas.stockflow.inventory.InventoryService;
import com.julianas.stockflow.inventory.StockMovementType;
import com.julianas.stockflow.product.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IdempotentInventoryApiIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductService products;
    @Autowired CategoryService categories;
    @Autowired InventoryService inventory;
    @Autowired IdempotentInventoryService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(StockMovementType.class)
    void lostResponseCanBeReplayedWithoutAnotherMovementEvenAfterOtherStockChanges(StockMovementType type) throws Exception {
        long id = product();
        String key = UUID.randomUUID().toString();
        var first = confirm(key, id, type, 2, " Count ").andExpect(status().isOk()).andReturn();
        inventory.increaseStock(id, 3, "Later movement");
        products.deactivate(id);
        var replay = confirm(key, id, type, 2, "Count").andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(replay.getResponse().getContentAsString()))
                .isEqualTo(json.readTree(first.getResponse().getContentAsString()));
        assertThat(products.getById(id).getStock()).isEqualTo(type == StockMovementType.IN ? 10 : 6);
        assertThat(count("inventory_confirmations")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(3);
        long movementId = json.readTree(first.getResponse().getContentAsString()).path("id").asLong();
        assertThatThrownBy(() -> jdbc.update("delete from stock_movements where id = ?", movementId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into inventory_confirmations (idempotency_key, request_hash, movement_id) values (?, ?, ?)",
                UUID.randomUUID(), "a".repeat(64), movementId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keyCannotBeReusedForAnotherProductDirectionQuantityOrReason() throws Exception {
        long id = product();
        long other = product();
        String key = UUID.randomUUID().toString();
        confirm(key, id, StockMovementType.IN, 2, "Count").andExpect(status().isOk());
        conflict(key, other, StockMovementType.IN, 2, "Count");
        conflict(key, id, StockMovementType.OUT, 2, "Count");
        conflict(key, id, StockMovementType.IN, 3, "Count");
        conflict(key, id, StockMovementType.IN, 2, "Other");
        assertThat(products.getById(id).getStock()).isEqualTo(7);
        assertThat(products.getById(other).getStock()).isEqualTo(5);
        assertThat(count("inventory_confirmations")).isEqualTo(1);
    }

    @Test
    void rejectedAndRolledBackRequestsLeaveNoConfirmationAndKeyRemainsReusable() throws Exception {
        long id = product();
        String key = UUID.randomUUID().toString();
        confirm(key, id, StockMovementType.OUT, 6, "Count").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(count("inventory_confirmations")).isZero();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.confirm(key, id, StockMovementType.IN, 2, "Count");
            status.setRollbackOnly();
        });
        assertThat(count("inventory_confirmations")).isZero();
        assertThat(count("stock_movements")).isEqualTo(1);
        assertThat(products.getById(id).getStock()).isEqualTo(5);
        confirm(key, id, StockMovementType.IN, 2, "Count").andExpect(status().isOk());
        assertThat(products.getById(id).getStock()).isEqualTo(7);
    }

    @Test
    void concurrentRetryReportsInProgressThenReplaysCommittedResult() throws Exception {
        long id = product();
        String key = UUID.randomUUID().toString();
        CountDownLatch beforeCommit = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
            var movement = service.confirm(key, id, StockMovementType.OUT, 2, "Count");
            beforeCommit.countDown();
            try {
                if (!commit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Commit gate timeout");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt(); throw new IllegalStateException(exception);
            }
            return movement.getId();
        }));
        try {
            assertThat(beforeCommit.await(10, TimeUnit.SECONDS)).isTrue();
            confirm(key, id, StockMovementType.OUT, 2, "Count").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("IDEMPOTENCY_IN_PROGRESS"));
            commit.countDown();
            long original = first.get(10, TimeUnit.SECONDS);
            confirm(key, id, StockMovementType.OUT, 2, "Count").andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(original));
            assertThat(products.getById(id).getStock()).isEqualTo(3);
            assertThat(count("stock_movements")).isEqualTo(2);
            assertThat(count("inventory_confirmations")).isEqualTo(1);
        } finally {
            commit.countDown(); executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void malformedKeyDoesNotChangeStockOrHistory() throws Exception {
        long id = product();
        for (String key : new String[]{"", "bad", "1-1-1-1-1"}) {
            confirm(key, id, StockMovementType.IN, 2, "Count").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        }
        assertThat(products.getById(id).getStock()).isEqualTo(5);
        assertThat(count("inventory_confirmations")).isZero();
        assertThat(count("stock_movements")).isEqualTo(1);
    }

    private void conflict(String key, long id, StockMovementType type, int quantity, String reason) throws Exception {
        confirm(key, id, type, quantity, reason).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private ResultActions confirm(String key, long id, StockMovementType type, int quantity, String reason) throws Exception {
        return mvc.perform(post("/api/products/{id}/stock/{direction}", id, type.name().toLowerCase(java.util.Locale.ROOT))
                .header("Idempotency-Key", key).contentType("application/json")
                .content(json.writeValueAsString(Map.of("quantity", quantity, "reason", reason))));
    }

    private long product() {
        String unique = UUID.randomUUID().toString();
        long category = categories.create("Inventory " + unique, null).getId();
        long id = products.create("Product", unique, null, BigDecimal.TEN, BigDecimal.ONE, 0, category).getId();
        inventory.increaseStock(id, 5, "Initial stock");
        return id;
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
}
