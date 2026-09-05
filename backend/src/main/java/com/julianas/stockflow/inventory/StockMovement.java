package com.julianas.stockflow.inventory;

import com.julianas.stockflow.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "stock_movements")
@Immutable
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private StockMovementType movementType;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @PositiveOrZero
    @Column(name = "stock_before", nullable = false)
    private Integer stockBefore;

    @NotNull
    @PositiveOrZero
    @Column(name = "stock_after", nullable = false)
    private Integer stockAfter;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    public StockMovement(
            Product product,
            StockMovementType movementType,
            Integer quantity,
            Integer stockBefore,
            Integer stockAfter,
            String reason
    ) {
        this.product = Objects.requireNonNull(product, "product must not be null");
        this.movementType = Objects.requireNonNull(movementType, "movementType must not be null");
        this.quantity = requirePositive(quantity, "quantity");
        this.stockBefore = requireNonNegative(stockBefore, "stockBefore");
        this.stockAfter = requireNonNegative(stockAfter, "stockAfter");
        this.reason = normalizeReason(reason);
        validateBalance();
    }

    @PrePersist
    private void initializeCreatedAt() {
        createdAt = Instant.now();
    }

    private static Integer requirePositive(Integer value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private static Integer requireNonNegative(Integer value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    private static String normalizeReason(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        String normalizedReason = reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (normalizedReason.length() > 255) {
            throw new IllegalArgumentException("reason must not exceed 255 characters");
        }
        return normalizedReason;
    }

    private void validateBalance() {
        long expectedStockAfter = movementType == StockMovementType.IN
                ? (long) stockBefore + quantity
                : (long) stockBefore - quantity;
        if (stockAfter.longValue() != expectedStockAfter) {
            throw new IllegalArgumentException("stockAfter does not match the movement balance");
        }
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public StockMovementType getMovementType() {
        return movementType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getStockBefore() {
        return stockBefore;
    }

    public Integer getStockAfter() {
        return stockAfter;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
