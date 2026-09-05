package com.julianas.stockflow.sale;

import com.julianas.stockflow.product.Product;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "sales")
@Immutable
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 12, fraction = 2)
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = new BigDecimal("0.00");

    @Size(max = 500)
    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<SaleItem> items = new ArrayList<>();

    protected Sale() {
    }

    public Sale(String notes) {
        this.notes = normalizeNotes(notes);
    }

    @PrePersist
    private void initializeCreatedAt() {
        createdAt = Instant.now();
    }

    SaleItem addItem(Product product, int quantity) {
        Objects.requireNonNull(product, "product must not be null");
        Long productId = Objects.requireNonNull(product.getId(), "product must be persisted");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (items.stream().anyMatch(item -> item.getProduct().getId().equals(productId))) {
            throw new IllegalArgumentException("product is already present in this sale");
        }

        SaleItem item = new SaleItem(this, product, quantity);
        BigDecimal newTotal = MonetaryValues.numeric14_2(total.add(item.getSubtotal()), "total");
        items.add(item);
        total = newTotal;
        return item;
    }

    private static String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String normalizedNotes = notes.trim();
        if (normalizedNotes.isEmpty()) {
            return null;
        }
        if (normalizedNotes.length() > 500) {
            throw new IllegalArgumentException("notes must not exceed 500 characters");
        }
        return normalizedNotes;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SaleItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
