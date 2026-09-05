package com.julianas.stockflow.inventory;

import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductNotFoundException;
import com.julianas.stockflow.product.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    public InventoryService(ProductRepository productRepository, StockMovementRepository stockMovementRepository) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository");
        this.stockMovementRepository = Objects.requireNonNull(stockMovementRepository, "stockMovementRepository");
    }

    @Transactional
    public StockMovement increaseStock(Long productId, Integer quantity, String reason) {
        MovementRequest request = validateMovementRequest(productId, quantity, reason);
        Product product = findByIdForUpdate(request.productId());
        int stockBefore = product.getStock();
        long stockAfterAsLong = (long) stockBefore + request.quantity();
        if (stockAfterAsLong > Integer.MAX_VALUE) {
            throw new StockLimitExceededException();
        }
        int stockAfter = (int) stockAfterAsLong;
        product.increaseStock(request.quantity());
        return stockMovementRepository.save(new StockMovement(
                product, StockMovementType.IN, request.quantity(), stockBefore, stockAfter, request.reason()
        ));
    }

    @Transactional
    public StockMovement decreaseStock(Long productId, Integer quantity, String reason) {
        MovementRequest request = validateMovementRequest(productId, quantity, reason);
        Product product = findByIdForUpdate(request.productId());
        int stockBefore = product.getStock();
        if (request.quantity() > stockBefore) {
            throw new InsufficientStockException(request.productId(), stockBefore, request.quantity());
        }
        int stockAfter = stockBefore - request.quantity();
        product.decreaseStock(request.quantity());
        return stockMovementRepository.save(new StockMovement(
                product, StockMovementType.OUT, request.quantity(), stockBefore, stockAfter, request.reason()
        ));
    }

    @Transactional(readOnly = true)
    public Page<StockMovement> getHistory(Long productId, Pageable pageable) {
        Objects.requireNonNull(productId, "productId");
        Pageable requiredPageable = Objects.requireNonNull(pageable, "pageable");
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException(productId);
        }
        return stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId, requiredPageable);
    }

    private Product findByIdForUpdate(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private static MovementRequest validateMovementRequest(Long productId, Integer quantity, String reason) {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(quantity, "quantity");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        String normalizedReason = Objects.requireNonNull(reason, "reason").trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (normalizedReason.length() > 255) {
            throw new IllegalArgumentException("reason must not exceed 255 characters");
        }
        return new MovementRequest(productId, quantity, normalizedReason);
    }

    private record MovementRequest(Long productId, int quantity, String reason) {
    }
}
