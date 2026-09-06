package com.julianas.stockflow.sale.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleResponse(
        Long id,
        BigDecimal total,
        String notes,
        Instant createdAt,
        List<SaleItemResponse> items
) {
    public SaleResponse {
        items = List.copyOf(items);
    }
}
