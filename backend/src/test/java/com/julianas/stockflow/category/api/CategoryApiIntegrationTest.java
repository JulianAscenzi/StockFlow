package com.julianas.stockflow.category.api;

import com.julianas.stockflow.api.ApiIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryApiIntegrationTest extends ApiIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsRetrievesListsAndUpdatesCategoryThroughHttp() throws Exception {
        long categoryId = createCategory("  Electronics  ", "Devices and accessories");

        mockMvc.perform(get("/api/categories/{id}", categoryId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(categoryId))
                .andExpect(jsonPath("$.name").value("Electronics"))
                .andExpect(jsonPath("$.description").value("Devices and accessories"));

        mockMvc.perform(get("/api/categories?page=0&size=1&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].id").value(categoryId))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(put("/api/categories/{id}", categoryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryUpdateRequest("  Home appliances ", "Updated description"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(categoryId))
                .andExpect(jsonPath("$.name").value("Home appliances"))
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    void rejectsDuplicatesMissingAndInvalidCategoryRequestsWithApiErrors() throws Exception {
        createCategory("Office", null);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest("OFFICE", null))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.path").value("/api/categories"))
                .andExpect(jsonPath("$.fieldErrors").isMap());

        mockMvc.perform(get("/api/categories/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.fieldErrors").isMap());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest("", null))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").isArray())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors").isMap());
    }

    @Test
    void deletesAnUnusedCategoryAndMakesItUnavailable() throws Exception {
        long categoryId = createCategory("Disposable", null);

        mockMvc.perform(delete("/api/categories/{id}", categoryId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categories/{id}", categoryId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    private long createCategory(String name, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CategoryCreateRequest(name, description))))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("http://localhost/api/categories/")))
                .andExpect(jsonPath("$.id", greaterThan(0)))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = response.get("id").asLong();
        org.junit.jupiter.api.Assertions.assertTrue(
                result.getResponse().getHeader("Location").endsWith("/" + id),
                "Location must identify the created category"
        );
        return id;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
