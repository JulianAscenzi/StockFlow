package com.julianas.stockflow.inventory.api;

import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.inventory.StockMovement;
import com.julianas.stockflow.inventory.StockMovementType;
import com.julianas.stockflow.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class StockMovementMapperTest {

    private final StockMovementMapper mapper = new StockMovementMapper();

    @Test
    void mapsAllFieldsAndAccessesOnlyProductId() {
        Instant createdAt = Instant.parse("2026-03-01T10:15:30Z");
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(8L);
        StockMovement movement = movement(21L, product, StockMovementType.IN, 5, 12, 17,
                "Supplier delivery", createdAt);

        StockMovementResponse response = mapper.toResponse(movement);

        assertEquals(21L, response.id());
        assertEquals(8L, response.productId());
        assertEquals(StockMovementType.IN, response.movementType());
        assertEquals(5, response.quantity());
        assertEquals(12, response.stockBefore());
        assertEquals(17, response.stockAfter());
        assertEquals("Supplier delivery", response.reason());
        assertEquals(createdAt, response.createdAt());
        verify(product).getId();
        verifyNoMoreInteractions(product);
    }

    @Test
    void preservesOutMovementType() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(9L);
        StockMovement movement = movement(22L, product, StockMovementType.OUT, 4, 12, 8,
                "Customer order", Instant.parse("2026-03-02T10:15:30Z"));

        StockMovementResponse response = mapper.toResponse(movement);

        assertEquals(StockMovementType.OUT, response.movementType());
        assertEquals(4, response.quantity());
        assertEquals(12, response.stockBefore());
        assertEquals(8, response.stockAfter());
    }

    @Test
    void rejectsNullMovement() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> mapper.toResponse(null));

        assertEquals("movement must not be null", exception.getMessage());
    }

    @Test
    void mapsPagePreservingOrderAndMetadata() {
        StockMovement first = movementWithId(31L, StockMovementType.IN, 1, 0, 1);
        StockMovement second = movementWithId(32L, StockMovementType.OUT, 1, 1, 0);
        Page<StockMovement> page = new PageImpl<>(List.of(first, second), PageRequest.of(1, 2), 5);

        PageResponse<StockMovementResponse> response = mapper.toPageResponse(page);

        assertEquals(List.of(31L, 32L), response.content().stream().map(StockMovementResponse::id).toList());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        assertEquals(false, response.first());
        assertEquals(false, response.last());
    }

    @Test
    void rejectsNullPage() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> mapper.toPageResponse(null));

        assertEquals("page must not be null", exception.getMessage());
    }

    private StockMovement movementWithId(Long id, StockMovementType type, int quantity, int before, int after) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id + 100);
        return movement(id, product, type, quantity, before, after, "Reason " + id,
                Instant.parse("2026-03-03T10:15:30Z"));
    }

    private StockMovement movement(
            Long id,
            Product product,
            StockMovementType type,
            Integer quantity,
            Integer stockBefore,
            Integer stockAfter,
            String reason,
            Instant createdAt
    ) {
        StockMovement movement = mock(StockMovement.class);
        when(movement.getId()).thenReturn(id);
        when(movement.getProduct()).thenReturn(product);
        when(movement.getMovementType()).thenReturn(type);
        when(movement.getQuantity()).thenReturn(quantity);
        when(movement.getStockBefore()).thenReturn(stockBefore);
        when(movement.getStockAfter()).thenReturn(stockAfter);
        when(movement.getReason()).thenReturn(reason);
        when(movement.getCreatedAt()).thenReturn(createdAt);
        return movement;
    }
}
