package com.julianas.stockflow.category.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description
) {
}
