package com.julianas.stockflow.product.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 50) String sku,
        @Size(max = 500) String description,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotNull @PositiveOrZero BigDecimal cost,
        @NotNull @PositiveOrZero Integer minimumStock,
        @NotNull @Positive Long categoryId
) {
}
