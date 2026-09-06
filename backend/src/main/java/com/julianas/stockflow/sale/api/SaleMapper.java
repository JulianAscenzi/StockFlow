package com.julianas.stockflow.sale.api;

import com.julianas.stockflow.sale.Sale;
import com.julianas.stockflow.sale.SaleItem;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SaleMapper {

    public SaleResponse toResponse(Sale sale) {
        Sale requiredSale = Objects.requireNonNull(sale, "sale must not be null");

        return new SaleResponse(
                requiredSale.getId(),
                requiredSale.getTotal(),
                requiredSale.getNotes(),
                requiredSale.getCreatedAt(),
                requiredSale.getItems().stream().map(this::toItemResponse).toList()
        );
    }

    private SaleItemResponse toItemResponse(SaleItem item) {
        SaleItem requiredItem = Objects.requireNonNull(item, "sale item must not be null");

        return new SaleItemResponse(
                requiredItem.getId(),
                requiredItem.getProduct().getId(),
                requiredItem.getProductName(),
                requiredItem.getProductSku(),
                requiredItem.getQuantity(),
                requiredItem.getUnitPrice(),
                requiredItem.getUnitCost(),
                requiredItem.getSubtotal()
        );
    }
}
