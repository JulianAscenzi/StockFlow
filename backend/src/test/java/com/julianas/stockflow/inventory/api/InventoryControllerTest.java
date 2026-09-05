package com.julianas.stockflow.inventory.api;

import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.common.error.GlobalExceptionHandler;
import com.julianas.stockflow.inventory.InsufficientStockException;
import com.julianas.stockflow.inventory.InventoryService;
import com.julianas.stockflow.inventory.StockLimitExceededException;
import com.julianas.stockflow.inventory.StockMovement;
import com.julianas.stockflow.inventory.StockMovementType;
import com.julianas.stockflow.product.ProductNotFoundException;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import(GlobalExceptionHandler.class)
class InventoryControllerTest {

    private static final long PRODUCT_ID = 41L;
    private static final Instant CREATED_AT = Instant.parse("2026-04-10T12:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private StockMovementMapper stockMovementMapper;

    private StockMovement movement;

    @BeforeEach
    void setUp() {
        movement = mock(StockMovement.class);
    }

    @Test
    void validInReturnsOkJsonAndDelegatesOnlyIncrease() throws Exception {
        StockMovementResponse response = response(StockMovementType.IN, 10, 15);
        when(inventoryService.increaseStock(PRODUCT_ID, 5, "Supplier delivery")).thenReturn(movement);
        when(stockMovementMapper.toResponse(movement)).thenReturn(response);

        mockMvc.perform(post("/api/products/{productId}/stock/in", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.movementType").value("IN"))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.stockBefore").value(10))
                .andExpect(jsonPath("$.stockAfter").value(15))
                .andExpect(jsonPath("$.reason").value("Supplier delivery"))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.product").doesNotExist())
                .andExpect(jsonPath("$.category").doesNotExist())
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist())
                .andExpect(jsonPath("$.handler").doesNotExist());

        verify(inventoryService).increaseStock(PRODUCT_ID, 5, "Supplier delivery");
        verify(inventoryService, never()).decreaseStock(any(), any(), any());
        verify(stockMovementMapper).toResponse(movement);
    }

    @Test
    void validOutReturnsOkJsonAndDelegatesOnlyDecrease() throws Exception {
        StockMovementResponse response = response(StockMovementType.OUT, 10, 6);
        when(inventoryService.decreaseStock(PRODUCT_ID, 4, "Customer order")).thenReturn(movement);
        when(stockMovementMapper.toResponse(movement)).thenReturn(response);

        mockMvc.perform(post("/api/products/{productId}/stock/out", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4,\"reason\":\"Customer order\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.movementType").value("OUT"))
                .andExpect(jsonPath("$.productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.stockBefore").value(10))
                .andExpect(jsonPath("$.stockAfter").value(6));

        verify(inventoryService).decreaseStock(PRODUCT_ID, 4, "Customer order");
        verify(inventoryService, never()).increaseStock(any(), any(), any());
        verify(stockMovementMapper).toResponse(movement);
    }

    @Test
    void invalidRequestsDoNotInvokeServiceOrMapper() throws Exception {
        String invalid = "{\"quantity\":0,\"reason\":\"   \"}";

        mockMvc.perform(post("/api/products/{productId}/stock/in", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/products/{productId}/stock/out", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(inventoryService, stockMovementMapper);
    }

    @Test
    void malformedAndMissingBodiesReturnBadRequestWithoutInteractions() throws Exception {
        mockMvc.perform(post("/api/products/{productId}/stock/in", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(post("/api/products/{productId}/stock/out", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(inventoryService, stockMovementMapper);
    }

    @Test
    void productNotFoundAndStockLimitAreMappedForIn() throws Exception {
        when(inventoryService.increaseStock(PRODUCT_ID, 5, "Supplier delivery"))
                .thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(post("/api/products/{productId}/stock/in", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        doThrow(new StockLimitExceededException())
                .when(inventoryService).increaseStock(PRODUCT_ID, 5, "Supplier delivery");

        mockMvc.perform(post("/api/products/{productId}/stock/in", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(requestJson()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STOCK_LIMIT_EXCEEDED"));
    }

    @Test
    void productNotFoundAndInsufficientStockAreMappedForOut() throws Exception {
        when(inventoryService.decreaseStock(PRODUCT_ID, 4, "Customer order"))
                .thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(post("/api/products/{productId}/stock/out", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4,\"reason\":\"Customer order\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        doThrow(new InsufficientStockException(PRODUCT_ID, 2, 4))
                .when(inventoryService).decreaseStock(PRODUCT_ID, 4, "Customer order");

        mockMvc.perform(post("/api/products/{productId}/stock/out", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":4,\"reason\":\"Customer order\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    void serverControlledRequestFieldsCannotInfluenceCommand() throws Exception {
        when(inventoryService.increaseStock(PRODUCT_ID, 5, "Supplier delivery")).thenReturn(movement);
        when(stockMovementMapper.toResponse(movement)).thenReturn(response(StockMovementType.IN, 10, 15));
        String body = "{\"quantity\":5,\"reason\":\"Supplier delivery\",\"productId\":99,"
                + "\"movementType\":\"OUT\",\"stockBefore\":900,\"stockAfter\":1,"
                + "\"createdAt\":\"2000-01-01T00:00:00Z\",\"id\":7}";

        mockMvc.perform(post("/api/products/{productId}/stock/in", PRODUCT_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("IN"))
                .andExpect(jsonPath("$.stockBefore").value(10))
                .andExpect(jsonPath("$.stockAfter").value(15));

        verify(inventoryService).increaseStock(PRODUCT_ID, 5, "Supplier delivery");
    }

    @Test
    void historyDelegatesSamePageableAndReturnsContentMetadataAndOrder() throws Exception {
        StockMovement first = mock(StockMovement.class);
        StockMovement second = mock(StockMovement.class);
        Page<StockMovement> page = new PageImpl<>(List.of(first, second), PageRequest.of(2, 3), 8);
        PageResponse<StockMovementResponse> response = new PageResponse<>(
                List.of(response(StockMovementType.OUT, 8, 3), response(StockMovementType.IN, 3, 8)),
                2, 3, 8, 3, false, true
        );
        when(inventoryService.getHistory(eq(PRODUCT_ID), any(Pageable.class))).thenReturn(page);
        when(stockMovementMapper.toPageResponse(page)).thenReturn(response);

        mockMvc.perform(get("/api/products/{productId}/stock-movements", PRODUCT_ID)
                        .param("page", "2").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].movementType").value("OUT"))
                .andExpect(jsonPath("$.content[1].movementType").value("IN"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryService).getHistory(eq(PRODUCT_ID), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(3, captor.getValue().getPageSize());
        verify(inventoryService, never()).increaseStock(any(), any(), any());
        verify(inventoryService, never()).decreaseStock(any(), any(), any());
        verify(stockMovementMapper).toPageResponse(page);
    }

    @Test
    void historyUsesDefaultPageSize20() throws Exception {
        Page<StockMovement> page = new PageImpl<>(List.of());
        when(inventoryService.getHistory(eq(PRODUCT_ID), any(Pageable.class))).thenReturn(page);
        when(stockMovementMapper.toPageResponse(page)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/products/{productId}/stock-movements", PRODUCT_ID))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryService).getHistory(eq(PRODUCT_ID), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void missingProductHistoryReturnsNotFoundWithoutCommandOperations() throws Exception {
        when(inventoryService.getHistory(eq(PRODUCT_ID), any(Pageable.class)))
                .thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(get("/api/products/{productId}/stock-movements", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));

        verify(inventoryService, never()).increaseStock(any(), any(), any());
        verify(inventoryService, never()).decreaseStock(any(), any(), any());
        verify(stockMovementMapper, never()).toPageResponse(any());
    }

    @Test
    void serializedRequestOnlyContainsClientControlledFields() throws Exception {
        String json = objectMapper.writeValueAsString(new StockMovementRequest(5, "Supplier delivery"));

        assertFalse(json.contains("productId"));
        assertFalse(json.contains("movementType"));
        assertFalse(json.contains("stockBefore"));
        assertFalse(json.contains("stockAfter"));
        assertFalse(json.contains("createdAt"));
        assertFalse(json.contains("id"));
    }

    private String requestJson() throws Exception {
        return objectMapper.writeValueAsString(new StockMovementRequest(5, "Supplier delivery"));
    }

    private StockMovementResponse response(StockMovementType type, int stockBefore, int stockAfter) {
        return new StockMovementResponse(
                8L, PRODUCT_ID, type, type == StockMovementType.IN ? 5 : 4,
                stockBefore, stockAfter, type == StockMovementType.IN ? "Supplier delivery" : "Customer order",
                CREATED_AT
        );
    }
}
