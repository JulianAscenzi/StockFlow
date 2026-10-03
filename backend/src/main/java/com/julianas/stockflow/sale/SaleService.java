package com.julianas.stockflow.sale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @Transactional(readOnly = true)
    public Page<Sale> history(int page, int size) {
        return saleRepository.findAllByOrderByCreatedAtDescIdDesc(
                PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional(readOnly = true)
    public Sale detail(Long id) {
        return saleRepository.findDetailById(id).orElseThrow(() -> new SaleNotFoundException(id));
    }

    @Transactional
    public Sale confirm(String notes, List<SaleLine> lines) {
        List<SaleLine> requiredLines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        Sale sale = new Sale(notes);
        for (SaleLine line : requiredLines.stream().sorted(Comparator.comparing(SaleLine::productId)).toList()) {
            SaleLine requiredLine = Objects.requireNonNull(line, "sale line");
            Product product = productRepository.findByIdForUpdate(requiredLine.productId())
                    .orElseThrow(() -> new ProductNotFoundException(requiredLine.productId()));
            requireActive(product);
            sale.addItem(product, requiredLine.quantity());
        }
        validate(sale);
        return persist(sale);
    }

    @Transactional
    public Sale confirm(Sale sale) {
        Sale requiredSale = Objects.requireNonNull(sale, "sale");
        validate(requiredSale);
        for (SaleItem item : requiredSale.getItems().stream()
                .sorted(Comparator.comparing(line -> line.getProduct().getId())).toList()) {
            Long id = item.getProduct().getId();
            Product current = productRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ProductNotFoundException(id));
            requireActive(current);
        }
        return persist(requiredSale);
    }

    private void requireActive(Product product) {
        if (!product.isActive()) throw new InactiveProductException(product.getId());
    }

    private void validate(Sale requiredSale) {
        if (requiredSale.getItems().isEmpty()) {
            throw new EmptySaleException();
        }

        BigDecimal expectedTotal = requiredSale.getItems().stream()
                .map(SaleItem::getSubtotal)
                .reduce(ZERO, BigDecimal::add);
        if (requiredSale.getTotal().compareTo(expectedTotal) != 0) {
            throw new IllegalArgumentException("sale total must equal the sum of item subtotals");
        }
    }

    private Sale persist(Sale requiredSale) {
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
