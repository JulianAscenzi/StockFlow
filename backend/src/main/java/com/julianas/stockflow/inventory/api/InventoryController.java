package com.julianas.stockflow.inventory.api;

import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.inventory.InventoryService;
import com.julianas.stockflow.inventory.StockMovement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products/{productId}")
public class InventoryController {

    private final InventoryService inventoryService;
    private final StockMovementMapper stockMovementMapper;

    public InventoryController(InventoryService inventoryService, StockMovementMapper stockMovementMapper) {
        this.inventoryService = inventoryService;
        this.stockMovementMapper = stockMovementMapper;
    }

    @PostMapping("/stock/in")
    public StockMovementResponse increaseStock(
            @PathVariable Long productId,
            @Valid @RequestBody StockMovementRequest request
    ) {
        StockMovement movement = inventoryService.increaseStock(productId, request.quantity(), request.reason());
        return stockMovementMapper.toResponse(movement);
    }

    @PostMapping("/stock/out")
    public StockMovementResponse decreaseStock(
            @PathVariable Long productId,
            @Valid @RequestBody StockMovementRequest request
    ) {
        StockMovement movement = inventoryService.decreaseStock(productId, request.quantity(), request.reason());
        return stockMovementMapper.toResponse(movement);
    }

    @GetMapping("/stock-movements")
    public PageResponse<StockMovementResponse> getHistory(
            @PathVariable Long productId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return stockMovementMapper.toPageResponse(inventoryService.getHistory(productId, pageable));
    }
}
