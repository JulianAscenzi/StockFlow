package com.julianas.stockflow.inventory.api;

import com.julianas.stockflow.api.ApiIntegrationTestSupport;
import com.julianas.stockflow.category.api.CategoryCreateRequest;
import com.julianas.stockflow.product.api.ProductCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryApiIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void recordsAnInboundMovementAndUpdatesProductStockThroughHttp() throws Exception {
        long productId = createProduct(createCategory());

        mockMvc.perform(stockIn(productId, 10, "  Delivery received  "))
                .andExpect(status().isOk())
                .andExpect(movementJson(productId, "IN", 10, 0, 10, "Delivery received"));

        assertProductStock(productId, 10);
    }

    @Test
    void recordsAnOutboundMovementAndUpdatesProductStockThroughHttp() throws Exception {
        long productId = createProduct(createCategory());
        performOk(stockIn(productId, 10, "Initial load"));

        mockMvc.perform(stockOut(productId, 4, "Customer sale"))
                .andExpect(status().isOk())
                .andExpect(movementJson(productId, "OUT", 4, 10, 6, "Customer sale"));

        assertProductStock(productId, 6);
    }

    @Test
    void returnsOnlyProductHistoryInDescendingOrderWithCorrectBalancesAndPaging() throws Exception {
        long productId = createProduct(createCategory());
        long otherProductId = createProduct(createCategory());
        performOk(stockIn(productId, 10, "Initial load"));
        performOk(stockOut(productId, 3, "Sale"));
        performOk(stockIn(productId, 2, "Correction"));
        performOk(stockIn(otherProductId, 99, "Other product"));

        mockMvc.perform(get("/api/products/{productId}/stock-movements?page=0&size=20", productId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].movementType").value("IN"))
                .andExpect(jsonPath("$.content[0].quantity").value(2))
                .andExpect(jsonPath("$.content[0].stockBefore").value(7))
                .andExpect(jsonPath("$.content[0].stockAfter").value(9))
                .andExpect(jsonPath("$.content[1].movementType").value("OUT"))
                .andExpect(jsonPath("$.content[1].quantity").value(3))
                .andExpect(jsonPath("$.content[1].stockBefore").value(10))
                .andExpect(jsonPath("$.content[1].stockAfter").value(7))
                .andExpect(jsonPath("$.content[2].movementType").value("IN"))
                .andExpect(jsonPath("$.content[2].quantity").value(10))
                .andExpect(jsonPath("$.content[2].stockBefore").value(0))
                .andExpect(jsonPath("$.content[2].stockAfter").value(10))
                .andExpect(jsonPath("$.content[0].productId").value(productId))
                .andExpect(jsonPath("$.content[1].productId").value(productId))
                .andExpect(jsonPath("$.content[2].productId").value(productId));

        mockMvc.perform(get("/api/products/{productId}/stock-movements?page=0&size=2", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void rejectsInsufficientStockWithoutChangingProductOrHistory() throws Exception {
        long productId = createProduct(createCategory());
        performOk(stockIn(productId, 5, "Initial load"));

        mockMvc.perform(stockOut(productId, 8, "Too many"))
                .andExpect(status().isConflict())
                .andExpect(apiError("INSUFFICIENT_STOCK"));

        assertProductStock(productId, 5);
        assertHistorySize(productId, 1);
    }

    @Test
    void rejectsStockPastIntegerMaximumWithoutCreatingAnotherMovement() throws Exception {
        long productId = createProduct(createCategory());
        performOk(stockIn(productId, Integer.MAX_VALUE, "Inventory migration"));

        mockMvc.perform(stockIn(productId, 1, "One more"))
                .andExpect(status().isConflict())
                .andExpect(apiError("STOCK_LIMIT_EXCEEDED"));

        assertProductStock(productId, Integer.MAX_VALUE);
        assertHistorySize(productId, 1);
    }

    @Test
    void returnsProductNotFoundForMissingProductInventoryEndpoints() throws Exception {
        long missingProductId = createProduct(createCategory()) + 1;

        mockMvc.perform(stockIn(missingProductId, 1, "Missing"))
                .andExpect(status().isNotFound())
                .andExpect(apiError("PRODUCT_NOT_FOUND"));
        mockMvc.perform(stockOut(missingProductId, 1, "Missing"))
                .andExpect(status().isNotFound())
                .andExpect(apiError("PRODUCT_NOT_FOUND"));
        mockMvc.perform(get("/api/products/{productId}/stock-movements", missingProductId))
                .andExpect(status().isNotFound())
                .andExpect(apiError("PRODUCT_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidHttpRequestsWithoutCreatingMovements() throws Exception {
        long productId = createProduct(createCategory());

        assertInvalidJsonRequest(productId, "{\"quantity\":null,\"reason\":\"Missing quantity\"}", "VALIDATION_ERROR");
        assertInvalidRequest(productId, Map.of("quantity", 0, "reason", "Zero"), "VALIDATION_ERROR");
        assertInvalidRequest(productId, Map.of("quantity", -1, "reason", "Negative"), "VALIDATION_ERROR");
        assertInvalidRequest(productId, Map.of("quantity", 1, "reason", ""), "VALIDATION_ERROR");
        assertInvalidRequest(productId, Map.of("quantity", 1, "reason", "x".repeat(256)), "VALIDATION_ERROR");

        mockMvc.perform(post("/api/products/{productId}/stock/in", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(apiError("MALFORMED_REQUEST"));
        assertProductStock(productId, 0);
        assertHistorySize(productId, 0);
    }

    @Test
    void permitsPhysicalInventoryCorrectionsForInactiveProducts() throws Exception {
        long productId = createProduct(createCategory());
        mockMvc.perform(patch("/api/products/{id}/deactivate", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(stockIn(productId, 1, "Physical count"))
                .andExpect(status().isOk())
                .andExpect(movementJson(productId, "IN", 1, 0, 1, "Physical count"));
        assertProductStock(productId, 1);
    }

    @Test
    void concurrentWithdrawalsAllowOneSuccessAndOneInsufficientStockError() throws Exception {
        long productId = createProduct(createCategory());
        performOk(stockIn(productId, 10, "Initial load"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MvcResult> first = executor.submit(() -> performConcurrentWithdrawal(productId, ready, start));
            Future<MvcResult> second = executor.submit(() -> performConcurrentWithdrawal(productId, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both HTTP requests must be ready before starting");
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(10, TimeUnit.SECONDS).getResponse().getStatus()
            );
            assertEquals(1, statuses.stream().filter(statusCode -> statusCode == 200).count());
            assertEquals(1, statuses.stream().filter(statusCode -> statusCode == 409).count());

            MvcResult failed = first.get().getResponse().getStatus() == 409 ? first.get() : second.get();
            assertEquals("INSUFFICIENT_STOCK", responseJson(failed).get("code").asString());
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Concurrent HTTP requests did not finish");
        }

        assertProductStock(productId, 3);
        MvcResult history = mockMvc.perform(get("/api/products/{productId}/stock-movements?page=0&size=20", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        JsonNode movements = responseJson(history).get("content");
        assertEquals(1, countMovements(movements, "IN", 10));
        assertEquals(1, countMovements(movements, "OUT", 7));
    }

    private MvcResult performConcurrentWithdrawal(long productId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS), "Concurrent HTTP requests were not released");
        return mockMvc.perform(stockOut(productId, 7, "Concurrent withdrawal")).andReturn();
    }

    private void assertInvalidRequest(long productId, Map<String, Object> request, String code) throws Exception {
        assertInvalidJsonRequest(productId, json(request), code);
    }

    private void assertInvalidJsonRequest(long productId, String request, String code) throws Exception {
        mockMvc.perform(post("/api/products/{productId}/stock/in", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(apiError(code));
        assertProductStock(productId, 0);
        assertHistorySize(productId, 0);
    }

    private void assertProductStock(long productId, int stock) throws Exception {
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stock").value(stock));
    }

    private void assertHistorySize(long productId, int expectedSize) throws Exception {
        mockMvc.perform(get("/api/products/{productId}/stock-movements?page=0&size=20", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(expectedSize))
                .andExpect(jsonPath("$.content.length()").value(expectedSize));
    }

    private long createCategory() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest("Inventory category " + System.nanoTime(), null))))
                .andExpect(status().isCreated())
                .andReturn();
        return responseJson(result).get("id").asLong();
    }

    private long createProduct(long categoryId) throws Exception {
        String unique = Long.toUnsignedString(System.nanoTime());
        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProductCreateRequest(
                                "Inventory product " + unique, "INV-" + unique, null,
                                new BigDecimal("10.00"), new BigDecimal("5.00"), 0, categoryId
                        ))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        return responseJson(result).get("id").asLong();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder stockIn(
            long productId, Integer quantity, String reason
    ) throws Exception {
        return stockRequest(productId, "in", quantity, reason);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder stockOut(
            long productId, Integer quantity, String reason
    ) throws Exception {
        return stockRequest(productId, "out", quantity, reason);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder stockRequest(
            long productId, String direction, Integer quantity, String reason
    ) throws Exception {
        return post("/api/products/{productId}/stock/{direction}", productId, direction)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("quantity", quantity, "reason", reason)));
    }

    private org.springframework.test.web.servlet.ResultMatcher movementJson(
            long productId, String type, int quantity, int before, int after, String reason
    ) {
        return result -> {
            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).match(result);
            jsonPath("$.id").isNumber().match(result);
            jsonPath("$.createdAt").isNotEmpty().match(result);
            jsonPath("$.productId").isNumber().match(result);
            jsonPath("$.productId").value(productId).match(result);
            jsonPath("$.movementType").value(type).match(result);
            jsonPath("$.quantity").value(quantity).match(result);
            jsonPath("$.stockBefore").value(before).match(result);
            jsonPath("$.stockAfter").value(after).match(result);
            jsonPath("$.reason").value(reason).match(result);
            jsonPath("$.product").doesNotExist().match(result);
            jsonPath("$.category").doesNotExist().match(result);
            jsonPath("$..hibernateLazyInitializer").doesNotExist().match(result);
        };
    }

    private org.springframework.test.web.servlet.ResultMatcher apiError(String code) {
        return result -> {
            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).match(result);
            jsonPath("$.code").value(code).match(result);
            jsonPath("$.fieldErrors").isMap().match(result);
            jsonPath("$.message").isNotEmpty().match(result);
            jsonPath("$.stackTrace").doesNotExist().match(result);
            jsonPath("$.sql").doesNotExist().match(result);
        };
    }

    private void performOk(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().isOk());
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long countMovements(JsonNode movements, String movementType, int quantity) {
        long count = 0;
        for (JsonNode movement : movements) {
            if (movementType.equals(movement.get("movementType").asString())
                    && quantity == movement.get("quantity").asInt()) {
                count++;
            }
        }
        return count;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
