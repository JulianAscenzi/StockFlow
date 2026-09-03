package com.julianas.stockflow.product.api;

import java.math.BigDecimal;

class ProductCreateRequestTest extends ProductRequestValidationTest<ProductCreateRequest> {

    @Override
    ProductCreateRequest createRequest(
            String name,
            String sku,
            String description,
            BigDecimal price,
            BigDecimal cost,
            Integer minimumStock,
            Long categoryId
    ) {
        return new ProductCreateRequest(name, sku, description, price, cost, minimumStock, categoryId);
    }
}
