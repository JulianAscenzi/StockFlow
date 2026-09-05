package com.julianas.stockflow.sale;

import com.julianas.stockflow.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "sale_items")
@Immutable
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @NotBlank
    @Size(max = 150)
    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @NotBlank
    @Size(max = 50)
    @Column(name = "product_sku", nullable = false, length = 50)
    private String productSku;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Column(name = "unit_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 12, fraction = 2)
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal;

    protected SaleItem() {
    }

    SaleItem(Sale sale, Product product, int quantity) {
        this.sale = Objects.requireNonNull(sale, "sale must not be null");
        this.product = Objects.requireNonNull(product, "product must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        productName = requireNotBlank(product.getName(), 150, "productName");
        productSku = requireNotBlank(product.getSku(), 50, "productSku");
        this.quantity = quantity;
        unitPrice = MonetaryValues.numeric12_2(product.getPrice(), "unitPrice");
        unitCost = MonetaryValues.numeric12_2(product.getCost(), "unitCost");
        subtotal = MonetaryValues.numeric14_2(unitPrice.multiply(BigDecimal.valueOf(quantity)), "subtotal");
    }

    private static String requireNotBlank(String value, int maximumLength, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maximumLength + " characters");
        }
        return value;
    }

    public Long getId() { return id; }
    public Sale getSale() { return sale; }
    public Product getProduct() { return product; }
    public String getProductName() { return productName; }
    public String getProductSku() { return productSku; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getSubtotal() { return subtotal; }
}
