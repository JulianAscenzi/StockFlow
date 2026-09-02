package com.julianas.stockflow.product;

import com.julianas.stockflow.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @NotBlank
    @Size(max = 50)
    @Column(name = "sku", nullable = false, length = 50, unique = true)
    private String sku;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @PositiveOrZero
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotNull
    @PositiveOrZero
    @Column(name = "cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal cost;

    @NotNull
    @PositiveOrZero
    @Column(name = "stock", nullable = false)
    private Integer stock;

    @NotNull
    @PositiveOrZero
    @Column(name = "minimum_stock", nullable = false)
    private Integer minimumStock;

    @Column(name = "active", nullable = false)
    private boolean active;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(
            String name,
            String sku,
            String description,
            BigDecimal price,
            BigDecimal cost,
            Integer stock,
            Integer minimumStock,
            boolean active,
            Category category
    ) {
        this.name = name;
        this.sku = normalizeSku(sku);
        this.description = description;
        this.price = price;
        this.cost = cost;
        this.stock = stock;
        this.minimumStock = minimumStock;
        this.active = active;
        this.category = category;
    }

    public void update(
            String name,
            String sku,
            String description,
            BigDecimal price,
            BigDecimal cost,
            Integer minimumStock,
            Category category
    ) {
        this.name = name;
        this.sku = normalizeSku(sku);
        this.description = description;
        this.price = price;
        this.cost = cost;
        this.minimumStock = minimumStock;
        this.category = category;
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    @PrePersist
    private void initializeTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }

    private static String normalizeSku(String sku) {
        return sku == null ? null : sku.trim().toUpperCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public Integer getStock() {
        return stock;
    }

    public Integer getMinimumStock() {
        return minimumStock;
    }

    public boolean isActive() {
        return active;
    }

    public Category getCategory() {
        return category;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
