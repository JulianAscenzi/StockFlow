package com.julianas.stockflow.inventory.api;

import com.julianas.stockflow.inventory.StockMovementType;

import java.time.Instant;

public record StockMovementResponse(
        Long id,
        Long productId,
        StockMovementType movementType,
        Integer quantity,
        Integer stockBefore,
        Integer stockAfter,
        String reason,
        Instant createdAt
) {
}
