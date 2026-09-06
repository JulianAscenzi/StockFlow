package com.julianas.stockflow.sale.api;

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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SaleApiIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void confirmsSaleWithSnapshotsStockDiscountAndMovementThroughHttp() throws Exception {
        long productId = createProduct(5);

        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("notes", "Counter sale", "items", java.util.List.of(Map.of("productId", productId, "quantity", 2))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.items[0].productName").value("Mouse"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(12.5))
                .andExpect(jsonPath("$.items[0].unitCost").value(7.25));

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(3));
        mockMvc.perform(get("/api/products/{id}/stock-movements", productId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].movementType").value("OUT"))
                .andExpect(jsonPath("$.content[0].quantity").value(2));
    }

    @Test
    void insufficientStockRollsBackSaleAndHistoryThroughHttp() throws Exception {
        long productId = createProduct(1);

        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("items", java.util.List.of(Map.of("productId", productId, "quantity", 2))))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(1));
        mockMvc.perform(get("/api/products/{id}/stock-movements", productId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    private long createProduct(int stock) throws Exception {
        MvcResult category = mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest("Sales " + System.nanoTime(), null))))
                .andExpect(status().isCreated()).andReturn();
        long categoryId = response(category).get("id").asLong();
        MvcResult product = mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ProductCreateRequest("Mouse", "MOU-" + System.nanoTime(), null,
                                new BigDecimal("12.50"), new BigDecimal("7.25"), 0, categoryId))))
                .andExpect(status().isCreated()).andReturn();
        long productId = response(product).get("id").asLong();
        mockMvc.perform(post("/api/products/{id}/stock/in", productId).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("quantity", stock, "reason", "Initial load"))))
                .andExpect(status().isOk());
        return productId;
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private JsonNode response(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
}
