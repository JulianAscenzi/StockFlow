package com.julianas.stockflow.sale.api;

import com.julianas.stockflow.api.ApiIntegrationTestSupport;
import com.julianas.stockflow.category.CategoryService;
import com.julianas.stockflow.inventory.InventoryService;
import com.julianas.stockflow.product.ProductService;
import com.julianas.stockflow.sale.IdempotentSaleService;
import com.julianas.stockflow.sale.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:5173"})
class IdempotentSaleApiIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductService products;
    @Autowired CategoryService categories;
    @Autowired InventoryService inventory;
    @Autowired IdempotentSaleService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void replayKeepsLocationSnapshotsAndExactlyOneStockMovementPerLine() throws Exception {
        long id = product("Original", 5);
        String key = UUID.randomUUID().toString();
        MvcResult first = confirm(key, payload(id, 2, " note ")).andExpect(status().isCreated()).andReturn();
        var createdAt = java.time.Instant.parse(json.readTree(first.getResponse().getContentAsString())
                .get("createdAt").asText());
        assertThat(createdAt.getNano() % 1_000).as("PostgreSQL microsecond precision").isZero();
        var product = products.getById(id);
        products.update(id, "Changed", "CHANGED", null, new BigDecimal("999"), new BigDecimal("888"), 0, product.getCategory().getId());
        products.deactivate(id);
        MvcResult replay = confirm(key, payload(id, 2, "note")).andExpect(status().isCreated()).andReturn();
        assertThat(replay.getResponse().getHeader("Location")).isEqualTo(first.getResponse().getHeader("Location"));
        assertThat(json.readTree(replay.getResponse().getContentAsString())).isEqualTo(json.readTree(first.getResponse().getContentAsString()));
        assertThat(products.getById(id).getStock()).isEqualTo(3);
        assertThat(count("sales")).isEqualTo(1);
        assertThat(count("sale_confirmations")).isEqualTo(1);
        assertThat(count("stock_movements")).isEqualTo(2);
        confirm(key, payload(id, 1, "note")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThatThrownBy(() -> jdbc.update("delete from sales")).isInstanceOf(DataIntegrityViolationException.class);
        long saleId = json.readTree(first.getResponse().getContentAsString()).get("id").asLong();
        assertThatThrownBy(() -> jdbc.update("insert into sale_confirmations (idempotency_key,request_hash,sale_id) values (?,?,?)",
                UUID.randomUUID(), "a".repeat(64), saleId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void inactiveRejectionRollsBackAndKeyCanBeReusedAfterActivation() throws Exception {
        long first = product("Active", 5);
        long second = product("Inactive", 5);
        products.deactivate(second);
        String key = UUID.randomUUID().toString();
        String body = json.writeValueAsString(Map.of("items", List.of(
                Map.of("productId", first, "quantity", 2), Map.of("productId", second, "quantity", 2))));
        confirm(key, body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"));
        assertThat(count("sales")).isZero();
        assertThat(count("sale_confirmations")).isZero();
        assertThat(count("stock_movements")).isEqualTo(2);
        assertThat(products.getById(first).getStock()).isEqualTo(5);
        assertThat(products.getById(second).getStock()).isEqualTo(5);
        inventory.increaseStock(second, 1, "Inactive adjustment");
        inventory.decreaseStock(second, 1, "Inactive adjustment");
        products.activate(second);
        confirm(key, body).andExpect(status().isCreated());
        assertThat(count("sale_confirmations")).isEqualTo(1);
    }

    @Test
    void rollbackLeavesKeyReusableAndRevertsEarlierStockChanges() throws Exception {
        long first = product("First", 5);
        long second = product("Second", 1);
        String key = UUID.randomUUID().toString();
        String body = json.writeValueAsString(Map.of("items", List.of(Map.of("productId", first, "quantity", 2), Map.of("productId", second, "quantity", 2))));
        confirm(key, body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertThat(count("sales")).isZero();
        assertThat(count("sale_confirmations")).isZero();
        assertThat(count("stock_movements")).isEqualTo(2);
        assertThat(products.getById(first).getStock()).isEqualTo(5);
        confirm(key, payload(first, 1, "")).andExpect(status().isCreated());
    }

    @Test
    void concurrentRequestReturnsInProgressThenReplaysCommittedSale() throws Exception {
        long id = product("Concurrent", 5);
        String key = UUID.randomUUID().toString();
        CountDownLatch beforeCommit = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var first = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
            var sale = service.confirm(key, null, List.of(new SaleService.SaleLine(id, 2)));
            beforeCommit.countDown();
            try {
                if (!commit.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Commit gate timeout");
            } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            return sale.getId();
        }));
        try {
            assertThat(beforeCommit.await(10, TimeUnit.SECONDS)).isTrue();
            confirm(key, payload(id, 2, "")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_IN_PROGRESS"));
            commit.countDown();
            long originalId = first.get(10, TimeUnit.SECONDS);
            confirm(key, payload(id, 2, "")).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(originalId));
            assertThat(products.getById(id).getStock()).isEqualTo(3);
            assertThat(count("sales")).isEqualTo(1);
            assertThat(count("stock_movements")).isEqualTo(2);
        } finally {
            commit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void allowsCrossOriginProductActivationAndDeactivation() throws Exception {
        for (String action : List.of("activate", "deactivate")) {
            mvc.perform(options("/api/products/1/" + action).header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "PATCH")
                            .header("Access-Control-Request-Headers", "Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Methods",
                            org.hamcrest.Matchers.containsString("PATCH")));
        }
    }

    @Test
    void validatesUuidDuplicatesAndCorsHeader() throws Exception {
        long id = product("Validation", 5);
        confirm("bad", payload(id, 1, "")).andExpect(status().isBadRequest());
        String body = json.writeValueAsString(Map.of("items", List.of(Map.of("productId", id, "quantity", 1), Map.of("productId", id, "quantity", 1))));
        confirm(UUID.randomUUID().toString(), body).andExpect(status().isBadRequest());
        assertThat(count("sale_confirmations")).isZero();
        assertThat(products.getById(id).getStock()).isEqualTo(5);
        mvc.perform(options("/api/sales").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type,Idempotency-Key,Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("Idempotency-Key")));
        mvc.perform(post("/api/sales").header("Origin", "http://localhost:5173").header("Idempotency-Key", UUID.randomUUID())
                        .contentType("application/json").content(payload(id, 1, "")))
                .andExpect(status().isCreated()).andExpect(header().string("Access-Control-Expose-Headers", "Location"));
    }

    private long product(String name, int stock) {
        var category = categories.create(name, null);
        var product = products.create(name, name, null, new BigDecimal("10"), new BigDecimal("4"), 0, category.getId());
        inventory.increaseStock(product.getId(), stock, "Fixture");
        return product.getId();
    }

    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    private String payload(long product, int quantity, String notes) throws Exception {
        return json.writeValueAsString(Map.of("notes", notes, "items", List.of(Map.of("productId", product, "quantity", quantity))));
    }
    private org.springframework.test.web.servlet.ResultActions confirm(String key, String body) throws Exception {
        return mvc.perform(post("/api/sales").header("Idempotency-Key", key).contentType("application/json").content(body));
    }
}
