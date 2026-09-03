package com.julianas.stockflow.product.api;

import java.math.BigDecimal;

class ProductUpdateRequestTest extends ProductRequestValidationTest<ProductUpdateRequest> {

    @Override
    ProductUpdateRequest createRequest(
            String name,
            String sku,
            String description,
            BigDecimal price,
            BigDecimal cost,
            Integer minimumStock,
            Long categoryId
    ) {
        return new ProductUpdateRequest(name, sku, description, price, cost, minimumStock, categoryId);
    }
}
