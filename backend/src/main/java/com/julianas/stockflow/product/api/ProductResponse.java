package com.julianas.stockflow.product.api;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String sku,
        String description,
        BigDecimal price,
        BigDecimal cost,
        Integer stock,
        Integer minimumStock,
        boolean active,
        Long categoryId,
        Instant createdAt,
        Instant updatedAt
) {
}
