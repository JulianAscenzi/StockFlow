package com.julianas.stockflow.product.api;

import com.julianas.stockflow.api.ApiIntegrationTestSupport;
import com.julianas.stockflow.category.api.CategoryCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductApiIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsReadsSearchesAndUpdatesAProductWithLazyCategoryMapping() throws Exception {
        long categoryId = createCategory("  Peripherals  ");
        long productId = createProduct(categoryId, "  Wireless Keyboard  ", " key-100 ");

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(productJson(productId, categoryId))
                .andExpect(jsonPath("$.price").value(1234.56))
                .andExpect(jsonPath("$.cost").value(789.01));

        mockMvc.perform(get("/api/products?page=0&size=1&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].id").value(productId))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/api/products/search?name=KEYBO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId));

        mockMvc.perform(get("/api/products/category/{categoryId}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId));

        mockMvc.perform(get("/api/products/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(productId));

        mockMvc.perform(put("/api/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest(
                                "Mechanical Keyboard", "key-101", "Updated", "1999.99", "1000.10", 7, categoryId
                        ))))
                .andExpect(status().isOk())
                .andExpect(productJson(productId, categoryId))
                .andExpect(jsonPath("$.name").value("Mechanical Keyboard"))
                .andExpect(jsonPath("$.sku").value("KEY-101"))
                .andExpect(jsonPath("$.price").value(1999.99))
                .andExpect(jsonPath("$.cost").value(1000.10))
                .andExpect(jsonPath("$.minimumStock").value(7))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void activatesAndDeactivatesProductsAndPreventsCategoryDeletionWhileInUse() throws Exception {
        long categoryId = createCategory("Hardware");
        long productId = createProduct(categoryId, "USB Hub", "hub-01");

        mockMvc.perform(patch("/api/products/{id}/deactivate", productId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/products/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(patch("/api/products/{id}/activate", productId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                        "/api/categories/{id}", categoryId))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void returnsExpectedApiErrorsForInvalidOrMissingProducts() throws Exception {
        long categoryId = createCategory("Office");
        createProduct(categoryId, "Mouse", "mouse-01");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("Another mouse", "MOUSE-01", null, "1.00", "0.50", 1, categoryId))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("PRODUCT_SKU_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.fieldErrors").isMap());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("Unknown category", "unknown-01", null, "1.00", "0.50", 1, 999_999L))))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isMap());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest("", "", null, "-1", "-1", -1, categoryId))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.sku").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.price").isNotEmpty());

        mockMvc.perform(get("/api/products/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    private long createCategory(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest(name, null))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createProduct(long categoryId, String name, String sku) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(productRequest(name, sku, "Compact peripheral", "1234.56", "789.01", 3, categoryId))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Location", startsWith("http://localhost/api/products/")))
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.sku").value("KEY-100".equalsIgnoreCase(sku.trim()) ? "KEY-100" : sku.trim().toUpperCase()))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$..hibernateLazyInitializer").doesNotExist())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = response.get("id").asLong();
        org.junit.jupiter.api.Assertions.assertTrue(
                result.getResponse().getHeader("Location").endsWith("/" + id),
                "Location must identify the created product"
        );
        return id;
    }

    private org.springframework.test.web.servlet.ResultMatcher productJson(long productId, long categoryId) {
        return result -> {
            jsonPath("$.id").value(productId).match(result);
            jsonPath("$.categoryId").value(categoryId).match(result);
            jsonPath("$.category").doesNotExist().match(result);
            jsonPath("$..hibernateLazyInitializer").doesNotExist().match(result);
            content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).match(result);
        };
    }

    private ProductCreateRequest productRequest(
            String name, String sku, String description, String price, String cost, int minimumStock, long categoryId
    ) {
        return new ProductCreateRequest(
                name,
                sku,
                description,
                new BigDecimal(price),
                new BigDecimal(cost),
                minimumStock,
                categoryId
        );
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
