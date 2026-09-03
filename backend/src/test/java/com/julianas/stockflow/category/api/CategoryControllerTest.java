package com.julianas.stockflow.category.api;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryInUseException;
import com.julianas.stockflow.category.CategoryNotFoundException;
import com.julianas.stockflow.category.CategoryService;
import com.julianas.stockflow.category.DuplicateCategoryNameException;
import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import(GlobalExceptionHandler.class)
class CategoryControllerTest {

    private static final long CATEGORY_ID = 7L;
    private static final Instant CREATED_AT = Instant.parse("2026-01-10T12:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-02-15T09:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CategoryService categoryService;

    @MockitoBean
    private CategoryMapper categoryMapper;

    private Category category;
    private CategoryResponse response;

    @BeforeEach
    void setUp() {
        category = org.mockito.Mockito.mock(Category.class);
        response = new CategoryResponse(
                CATEGORY_ID,
                "Electronics",
                "Electronic products",
                CREATED_AT,
                UPDATED_AT
        );
    }

    @Test
    void validPostReturnsCreated() throws Exception {
        configureCreate();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isCreated());
    }

    @Test
    void postReturnsCorrectLocation() throws Exception {
        configureCreate();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/categories/" + CATEGORY_ID
                ));
    }

    @Test
    void postReturnsCategoryResponseAsJson() throws Exception {
        configureCreate();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(jsonPath("$.name").value("Electronics"))
                .andExpect(jsonPath("$.description").value("Electronic products"))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt").value(UPDATED_AT.toString()));
    }

    @Test
    void postDelegatesExactRequestDataToService() throws Exception {
        configureCreate();

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isCreated());

        verify(categoryService).create("Electronics", "Electronic products");
        verify(categoryMapper).toResponse(category);
    }

    @Test
    void invalidPostReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryCreateRequest("", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
    }

    @Test
    void invalidPostDoesNotInvokeServiceOrMapper() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryCreateRequest("", null))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(categoryService, categoryMapper);
    }

    @Test
    void duplicatePostReturnsConflict() throws Exception {
        when(categoryService.create("Electronics", "Electronic products"))
                .thenThrow(new DuplicateCategoryNameException("Electronics"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"));
    }

    @Test
    void getByIdReturnsExpectedJson() throws Exception {
        configureGetById();

        mockMvc.perform(get("/api/categories/{id}", CATEGORY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID))
                .andExpect(jsonPath("$.name").value("Electronics"))
                .andExpect(jsonPath("$.description").value("Electronic products"));

        verify(categoryService).getById(CATEGORY_ID);
        verify(categoryMapper).toResponse(category);
    }

    @Test
    void missingGetReturnsNotFound() throws Exception {
        when(categoryService.getById(CATEGORY_ID))
                .thenThrow(new CategoryNotFoundException(CATEGORY_ID));

        mockMvc.perform(get("/api/categories/{id}", CATEGORY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void listUsesRequestedPaginationAndSort() throws Exception {
        Page<Category> page = new PageImpl<>(List.of(category));
        when(categoryService.findAll(any(Pageable.class))).thenReturn(page);
        when(categoryMapper.toPageResponse(page)).thenReturn(pageResponse());

        mockMvc.perform(get("/api/categories")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "name,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(categoryService).findAll(captor.capture());
        Pageable pageable = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(2, pageable.getPageNumber());
        org.junit.jupiter.api.Assertions.assertEquals(5, pageable.getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals(
                Sort.Direction.DESC,
                pageable.getSort().getOrderFor("name").getDirection()
        );
    }

    @Test
    void listReturnsPageContentAndMetadata() throws Exception {
        Page<Category> page = new PageImpl<>(
                List.of(category),
                PageRequest.of(1, 2, Sort.by("name")),
                5
        );
        when(categoryService.findAll(any(Pageable.class))).thenReturn(page);
        when(categoryMapper.toPageResponse(page)).thenReturn(pageResponse());

        mockMvc.perform(get("/api/categories?page=1&size=2&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(CATEGORY_ID))
                .andExpect(jsonPath("$.content[0].name").value("Electronics"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void validPutReturnsOkAndDelegatesFields() throws Exception {
        when(categoryService.update(CATEGORY_ID, "Home", "Home products"))
                .thenReturn(category);
        when(categoryMapper.toResponse(category)).thenReturn(response);

        mockMvc.perform(put("/api/categories/{id}", CATEGORY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CATEGORY_ID));

        verify(categoryService).update(CATEGORY_ID, "Home", "Home products");
        verify(categoryMapper).toResponse(category);
    }

    @Test
    void invalidPutReturnsBadRequestWithoutInvokingService() throws Exception {
        mockMvc.perform(put("/api/categories/{id}", CATEGORY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryUpdateRequest("", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(categoryService, never()).update(any(), any(), any());
        verifyNoInteractions(categoryMapper);
    }

    @Test
    void missingPutReturnsNotFound() throws Exception {
        when(categoryService.update(CATEGORY_ID, "Home", "Home products"))
                .thenThrow(new CategoryNotFoundException(CATEGORY_ID));

        mockMvc.perform(put("/api/categories/{id}", CATEGORY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void duplicatePutReturnsConflict() throws Exception {
        when(categoryService.update(CATEGORY_ID, "Home", "Home products"))
                .thenThrow(new DuplicateCategoryNameException("Home"));

        mockMvc.perform(put("/api/categories/{id}", CATEGORY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"));
    }

    @Test
    void validDeleteReturnsNoContentWithEmptyBody() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", CATEGORY_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(categoryService).delete(CATEGORY_ID);
        verifyNoInteractions(categoryMapper);
    }

    @Test
    void missingDeleteReturnsNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new CategoryNotFoundException(CATEGORY_ID))
                .when(categoryService).delete(CATEGORY_ID);

        mockMvc.perform(delete("/api/categories/{id}", CATEGORY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void categoryInUseDeleteReturnsConflict() throws Exception {
        org.mockito.Mockito.doThrow(new CategoryInUseException(CATEGORY_ID))
                .when(categoryService).delete(CATEGORY_ID);

        mockMvc.perform(delete("/api/categories/{id}", CATEGORY_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
    }

    @Test
    void endpointsDoNotSerializeCategoryInternals() throws Exception {
        configureGetById();

        mockMvc.perform(get("/api/categories/{id}", CATEGORY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist())
                .andExpect(jsonPath("$.handler").doesNotExist())
                .andExpect(content().string(not(containsString(Category.class.getName()))));

        verify(categoryMapper).toResponse(category);
    }

    @Test
    void responsesWithBodyUseApplicationJson() throws Exception {
        configureGetById();

        mockMvc.perform(get("/api/categories/{id}", CATEGORY_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    private void configureCreate() {
        when(categoryService.create("Electronics", "Electronic products")).thenReturn(category);
        when(categoryMapper.toResponse(category)).thenReturn(response);
    }

    private void configureGetById() {
        when(categoryService.getById(CATEGORY_ID)).thenReturn(category);
        when(categoryMapper.toResponse(category)).thenReturn(response);
    }

    private String createRequestJson() throws Exception {
        return objectMapper.writeValueAsString(
                new CategoryCreateRequest("Electronics", "Electronic products")
        );
    }

    private String updateRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new CategoryUpdateRequest("Home", "Home products"));
    }

    private PageResponse<CategoryResponse> pageResponse() {
        return new PageResponse<>(
                List.of(response),
                1,
                2,
                5,
                3,
                false,
                false
        );
    }
}
