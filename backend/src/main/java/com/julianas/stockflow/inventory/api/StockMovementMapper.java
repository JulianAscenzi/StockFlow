package com.julianas.stockflow.inventory.api;

import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.inventory.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class StockMovementMapper {

    public StockMovementResponse toResponse(StockMovement movement) {
        Objects.requireNonNull(movement, "movement must not be null");

        return new StockMovementResponse(
                movement.getId(),
                movement.getProduct().getId(),
                movement.getMovementType(),
                movement.getQuantity(),
                movement.getStockBefore(),
                movement.getStockAfter(),
                movement.getReason(),
                movement.getCreatedAt()
        );
    }

    public PageResponse<StockMovementResponse> toPageResponse(Page<StockMovement> page) {
        Objects.requireNonNull(page, "page must not be null");

        return PageResponse.from(page.map(this::toResponse));
    }
}
