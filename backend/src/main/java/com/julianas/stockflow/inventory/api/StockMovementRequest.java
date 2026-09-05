package com.julianas.stockflow.inventory.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StockMovementRequest(
        @NotNull @Positive Integer quantity,
        @NotBlank @Size(max = 255) String reason
) {
}
