package com.julianas.stockflow.sale.api;

import java.math.BigDecimal;

public record SaleItemResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal unitCost,
        BigDecimal subtotal
) {
}
