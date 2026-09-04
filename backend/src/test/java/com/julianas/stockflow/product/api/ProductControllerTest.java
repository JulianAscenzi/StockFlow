package com.julianas.stockflow.product.api;

import com.julianas.stockflow.category.CategoryNotFoundException;
import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.common.error.GlobalExceptionHandler;
import com.julianas.stockflow.product.DuplicateProductSkuException;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductNotFoundException;
import com.julianas.stockflow.product.ProductService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    private static final long PRODUCT_ID = 11L;
    private static final long CATEGORY_ID = 7L;
    private static final BigDecimal PRICE = new BigDecimal("19.99");
    private static final BigDecimal COST = new BigDecimal("8.50");
    private static final Instant CREATED_AT = Instant.parse("2026-01-10T12:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-02-15T09:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ProductMapper productMapper;

    private Product product;
    private ProductResponse activeResponse;
    private ProductResponse inactiveResponse;

    @BeforeEach
    void setUp() {
        product = org.mockito.Mockito.mock(Product.class);
        activeResponse = response(true);
        inactiveResponse = response(false);
    }

    @Test
    void validPostReturnsCreatedWithLocationAndAllResponseFields() throws Exception {
        configureCreate();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/products/" + PRODUCT_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(PRODUCT_ID))
                .andExpect(jsonPath("$.name").value("USB Cable"))
                .andExpect(jsonPath("$.sku").value("USB-001"))
                .andExpect(jsonPath("$.description").value("Braided cable"))
                .andExpect(jsonPath("$.price").value(19.99))
                .andExpect(jsonPath("$.cost").value(8.50))
                .andExpect(jsonPath("$.stock").value(12))
                .andExpect(jsonPath("$.minimumStock").value(3))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt").value(UPDATED_AT.toString()));
    }

    @Test
    void postDelegatesExactRequestFieldsAndMapsResult() throws Exception {
        configureCreate();

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isCreated());

        verify(productService).create(
                "USB Cable", "USB-001", "Braided cable", PRICE, COST, 3, CATEGORY_ID
        );
        verify(productMapper).toResponse(product);
    }

    @Test
    void createRequestDoesNotExposeStockOrActive() throws Exception {
        String json = createRequestJson();

        assertFalse(json.contains("stock"));
        assertFalse(json.contains("active"));
    }

    @Test
    void invalidPostReturnsValidationErrorWithoutInteractions() throws Exception {
        ProductCreateRequest invalid = new ProductCreateRequest(
                "", "", null, new BigDecimal("-1"), COST, -1, 0L
        );

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors.sku").isNotEmpty());

        verifyNoInteractions(productService, productMapper);
    }

    @Test
    void duplicateSkuPostReturnsConflict() throws Exception {
        when(productService.create(
                "USB Cable", "USB-001", "Braided cable", PRICE, COST, 3, CATEGORY_ID
        )).thenThrow(new DuplicateProductSkuException("USB-001"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_SKU_ALREADY_EXISTS"));
    }

    @Test
    void missingCategoryPostReturnsNotFound() throws Exception {
        when(productService.create(
                "USB Cable", "USB-001", "Braided cable", PRICE, COST, 3, CATEGORY_ID
        )).thenThrow(new CategoryNotFoundException(CATEGORY_ID));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void getByIdReturnsExpectedProduct() throws Exception {
        configureGetById(activeResponse);

        mockMvc.perform(get("/api/products/{id}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID))
                .andExpect(jsonPath("$.sku").value("USB-001"));

        verify(productService).getById(PRODUCT_ID);
        verify(productMapper).toResponse(product);
    }

    @Test
    void missingGetByIdReturnsNotFound() throws Exception {
        when(productService.getById(PRODUCT_ID)).thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(get("/api/products/{id}", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void listDelegatesRequestedPageableWithoutRebuildingIt() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productService.findAll(any(Pageable.class))).thenReturn(page);
        when(productMapper.toPageResponse(page)).thenReturn(pageResponse());

        mockMvc.perform(get("/api/products")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "name,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).findAll(captor.capture());
        assertPageable(captor.getValue(), 2, 5, Sort.Direction.DESC);
    }

    @Test
    void listReturnsContentAndMetadata() throws Exception {
        Page<Product> page = new PageImpl<>(
                List.of(product), PageRequest.of(1, 2, Sort.by("name")), 5
        );
        when(productService.findAll(any(Pageable.class))).thenReturn(page);
        when(productMapper.toPageResponse(page)).thenReturn(pageResponse());

        mockMvc.perform(get("/api/products?page=1&size=2&sort=name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(PRODUCT_ID))
                .andExpect(jsonPath("$.content[0].categoryId").value(CATEGORY_ID))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void searchDelegatesNameAndPageableAndOnlyUsesSearchServiceMethod() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productService.searchByName(any(), any(Pageable.class))).thenReturn(page);
        when(productMapper.toPageResponse(page)).thenReturn(pageResponse());

        mockMvc.perform(get("/api/products/search")
                        .param("name", "Cable")
                        .param("page", "3")
                        .param("size", "4")
                        .param("sort", "name,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).searchByName(org.mockito.ArgumentMatchers.eq("Cable"), captor.capture());
        assertPageable(captor.getValue(), 3, 4, Sort.Direction.DESC);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void absentAndEmptySearchNameDelegateEmptyValueAndReturnInvalidArgument() throws Exception {
        when(productService.searchByName(org.mockito.ArgumentMatchers.eq(""), any(Pageable.class)))
                .thenThrow(new IllegalArgumentException("blank search"));

        mockMvc.perform(get("/api/products/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        mockMvc.perform(get("/api/products/search").param("name", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        verify(productService, times(2))
                .searchByName(org.mockito.ArgumentMatchers.eq(""), any(Pageable.class));
        verifyNoMoreInteractions(productService);
    }

    @Test
    void categoryFilterDelegatesIdAndPageableAndOnlyUsesCategoryServiceMethod() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productService.findByCategory(any(), any(Pageable.class))).thenReturn(page);
        when(productMapper.toPageResponse(page)).thenReturn(pageResponse());

        mockMvc.perform(get("/api/products/category/{categoryId}", CATEGORY_ID)
                        .param("page", "1")
                        .param("size", "6")
                        .param("sort", "name,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).findByCategory(org.mockito.ArgumentMatchers.eq(CATEGORY_ID), captor.capture());
        assertPageable(captor.getValue(), 1, 6, Sort.Direction.ASC);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void missingCategoryFilterReturnsNotFound() throws Exception {
        when(productService.findByCategory(
                org.mockito.ArgumentMatchers.eq(CATEGORY_ID), any(Pageable.class)
        )).thenThrow(new CategoryNotFoundException(CATEGORY_ID));

        mockMvc.perform(get("/api/products/category/{categoryId}", CATEGORY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void activeRouteDelegatesPageableAndOnlyUsesFindActive() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productService.findActive(any(Pageable.class))).thenReturn(page);
        when(productMapper.toPageResponse(page)).thenReturn(pageResponse());

        mockMvc.perform(get("/api/products/active")
                        .param("page", "2")
                        .param("size", "8")
                        .param("sort", "name,desc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).findActive(captor.capture());
        assertPageable(captor.getValue(), 2, 8, Sort.Direction.DESC);
        verifyNoMoreInteractions(productService);
    }

    @Test
    void literalRoutesUseDefaultPageSizeAndNameSort() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productService.findActive(any(Pageable.class))).thenReturn(page);
        when(productMapper.toPageResponse(page)).thenReturn(pageResponse());

        mockMvc.perform(get("/api/products/active"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productService).findActive(captor.capture());
        assertPageable(captor.getValue(), 0, 20, Sort.Direction.ASC);
    }

    @Test
    void validPutReturnsOkAndDelegatesEveryRequestField() throws Exception {
        when(productService.update(
                PRODUCT_ID, "USB-C Cable", "USB-002", "Two meters",
                new BigDecimal("24.99"), new BigDecimal("10.25"), 5, CATEGORY_ID
        )).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(activeResponse);

        mockMvc.perform(put("/api/products/{id}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID));

        verify(productService).update(
                PRODUCT_ID, "USB-C Cable", "USB-002", "Two meters",
                new BigDecimal("24.99"), new BigDecimal("10.25"), 5, CATEGORY_ID
        );
        verify(productMapper).toResponse(product);
    }

    @Test
    void updateRequestDoesNotExposeStockOrActive() throws Exception {
        String json = updateRequestJson();

        assertFalse(json.contains("stock"));
        assertFalse(json.contains("active"));
    }

    @Test
    void invalidPutReturnsValidationErrorWithoutInteractions() throws Exception {
        ProductUpdateRequest invalid = new ProductUpdateRequest(
                "", "", null, null, COST, -1, null
        );

        mockMvc.perform(put("/api/products/{id}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(productService, never()).update(any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(productMapper);
    }

    @Test
    void missingProductPutReturnsNotFound() throws Exception {
        configureUpdateException(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(put("/api/products/{id}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void duplicateSkuPutReturnsConflict() throws Exception {
        configureUpdateException(new DuplicateProductSkuException("USB-002"));

        mockMvc.perform(put("/api/products/{id}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_SKU_ALREADY_EXISTS"));
    }

    @Test
    void missingCategoryPutReturnsNotFound() throws Exception {
        configureUpdateException(new CategoryNotFoundException(CATEGORY_ID));

        mockMvc.perform(put("/api/products/{id}", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void activateReturnsActiveProduct() throws Exception {
        when(productService.activate(PRODUCT_ID)).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(activeResponse);

        mockMvc.perform(patch("/api/products/{id}/activate", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        verify(productService).activate(PRODUCT_ID);
        verify(productMapper).toResponse(product);
    }

    @Test
    void deactivateReturnsInactiveProduct() throws Exception {
        when(productService.deactivate(PRODUCT_ID)).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(inactiveResponse);

        mockMvc.perform(patch("/api/products/{id}/deactivate", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(productService).deactivate(PRODUCT_ID);
        verify(productMapper).toResponse(product);
    }

    @Test
    void activatingMissingProductReturnsNotFound() throws Exception {
        when(productService.activate(PRODUCT_ID)).thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(patch("/api/products/{id}/activate", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void deactivatingMissingProductReturnsNotFound() throws Exception {
        when(productService.deactivate(PRODUCT_ID)).thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(patch("/api/products/{id}/deactivate", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void responsesUseFlatCategoryIdDecimalNumbersAndNoProductInternals() throws Exception {
        configureGetById(activeResponse);

        mockMvc.perform(get("/api/products/{id}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.categoryId").value(CATEGORY_ID))
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.price").isNumber())
                .andExpect(jsonPath("$.price").value(19.99))
                .andExpect(jsonPath("$.cost").isNumber())
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist())
                .andExpect(jsonPath("$.handler").doesNotExist())
                .andExpect(content().string(not(containsString(Product.class.getName()))));
    }

    private void configureCreate() {
        when(productService.create(
                "USB Cable", "USB-001", "Braided cable", PRICE, COST, 3, CATEGORY_ID
        )).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(activeResponse);
    }

    private void configureGetById(ProductResponse response) {
        when(productService.getById(PRODUCT_ID)).thenReturn(product);
        when(productMapper.toResponse(product)).thenReturn(response);
    }

    private void configureUpdateException(RuntimeException exception) {
        when(productService.update(
                PRODUCT_ID, "USB-C Cable", "USB-002", "Two meters",
                new BigDecimal("24.99"), new BigDecimal("10.25"), 5, CATEGORY_ID
        )).thenThrow(exception);
    }

    private String createRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new ProductCreateRequest(
                "USB Cable", "USB-001", "Braided cable", PRICE, COST, 3, CATEGORY_ID
        ));
    }

    private String updateRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new ProductUpdateRequest(
                "USB-C Cable", "USB-002", "Two meters",
                new BigDecimal("24.99"), new BigDecimal("10.25"), 5, CATEGORY_ID
        ));
    }

    private ProductResponse response(boolean active) {
        return new ProductResponse(
                PRODUCT_ID,
                "USB Cable",
                "USB-001",
                "Braided cable",
                PRICE,
                COST,
                12,
                3,
                active,
                CATEGORY_ID,
                CREATED_AT,
                UPDATED_AT
        );
    }

    private PageResponse<ProductResponse> pageResponse() {
        return new PageResponse<>(List.of(activeResponse), 1, 2, 5, 3, false, false);
    }

    private static void assertPageable(
            Pageable pageable,
            int expectedPage,
            int expectedSize,
            Sort.Direction expectedDirection
    ) {
        assertEquals(expectedPage, pageable.getPageNumber());
        assertEquals(expectedSize, pageable.getPageSize());
        Sort.Order nameOrder = pageable.getSort().getOrderFor("name");
        assertNotNull(nameOrder);
        assertEquals(expectedDirection, nameOrder.getDirection());
    }
}
