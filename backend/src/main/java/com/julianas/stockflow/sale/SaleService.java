package com.julianas.stockflow.sale;

import com.julianas.stockflow.inventory.InventoryService;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductNotFoundException;
import com.julianas.stockflow.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class SaleService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final SaleRepository saleRepository;
    private final InventoryService inventoryService;
    private final ProductRepository productRepository;

    public SaleService(
            SaleRepository saleRepository,
            InventoryService inventoryService,
            ProductRepository productRepository
    ) {
        this.saleRepository = Objects.requireNonNull(saleRepository, "saleRepository");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository");
    }

    @Transactional
    public Sale confirm(String notes, List<SaleLine> lines) {
        List<SaleLine> requiredLines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        Sale sale = new Sale(notes);
        for (SaleLine line : requiredLines) {
            SaleLine requiredLine = Objects.requireNonNull(line, "sale line");
            Product product = productRepository.findById(requiredLine.productId())
                    .orElseThrow(() -> new ProductNotFoundException(requiredLine.productId()));
            sale.addItem(product, requiredLine.quantity());
        }
        return confirm(sale);
    }

    @Transactional
    public Sale confirm(Sale sale) {
        Sale requiredSale = Objects.requireNonNull(sale, "sale");
        if (requiredSale.getItems().isEmpty()) {
            throw new EmptySaleException();
        }

        BigDecimal expectedTotal = requiredSale.getItems().stream()
                .map(SaleItem::getSubtotal)
                .reduce(ZERO, BigDecimal::add);
        if (requiredSale.getTotal().compareTo(expectedTotal) != 0) {
            throw new IllegalArgumentException("sale total must equal the sum of item subtotals");
        }

        List<SaleItem> itemsByProductId = requiredSale.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .toList();
        for (SaleItem item : itemsByProductId) {
            inventoryService.decreaseStock(item.getProduct().getId(), item.getQuantity(), "Sale");
        }

        return saleRepository.save(requiredSale);
    }

    public record SaleLine(Long productId, Integer quantity) {
    }
}
