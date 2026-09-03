package com.julianas.stockflow.product.api;

import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        Objects.requireNonNull(product, "product must not be null");

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getDescription(),
                product.getPrice(),
                product.getCost(),
                product.getStock(),
                product.getMinimumStock(),
                product.isActive(),
                product.getCategory().getId(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    public PageResponse<ProductResponse> toPageResponse(Page<Product> page) {
        Objects.requireNonNull(page, "page must not be null");

        return PageResponse.from(page.map(this::toResponse));
    }
}
