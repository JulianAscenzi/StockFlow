package com.julianas.stockflow.sale.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaleCreateRequest(
        @Size(max = 500) String notes,
        @NotEmpty List<@Valid SaleItemRequest> items
) {
    public SaleCreateRequest {
        if (items != null) {
            items = List.copyOf(items);
        }
    }
}
