package com.julianas.stockflow.product;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryNotFoundException;
import com.julianas.stockflow.category.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

@Service
public class ProductService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository
    ) {
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
    }

    @Transactional
    public Product create(
            String name,
            String sku,
            String description,
            BigDecimal price,
            BigDecimal cost,
            Integer minimumStock,
            Long categoryId
    ) {
        String normalizedName = normalizeName(name);
        String normalizedSku = normalizeSku(sku);
        validateAmounts(price, cost, minimumStock);
        Objects.requireNonNull(categoryId, "categoryId");
        rejectDuplicateSku(normalizedSku);
        Category category = findCategoryById(categoryId);

        Product product = new Product(
                normalizedName,
                normalizedSku,
                description,
                price,
                cost,
                0,
                minimumStock,
                true,
                category
        );
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product getById(Long id) {
        Objects.requireNonNull(id, "id");
        return findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(Objects.requireNonNull(pageable, "pageable"));
    }

    @Transactional(readOnly = true)
    public Page<Product> findByCategory(Long categoryId, Pageable pageable) {
        Objects.requireNonNull(categoryId, "categoryId");
        Pageable requiredPageable = Objects.requireNonNull(pageable, "pageable");
        ensureCategoryExists(categoryId);
        return productRepository.findByCategoryId(categoryId, requiredPageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> searchByName(String name, Pageable pageable) {
        String normalizedName = normalizeSearchName(name);
        return productRepository.findByNameContainingIgnoreCase(
                normalizedName,
                Objects.requireNonNull(pageable, "pageable")
        );
    }

    @Transactional(readOnly = true)
    public Page<Product> findActive(Pageable pageable) {
        return productRepository.findByActiveTrue(Objects.requireNonNull(pageable, "pageable"));
    }

    @Transactional
    public Product update(
            Long id,
            String name,
            String sku,
            String description,
            BigDecimal price,
            BigDecimal cost,
            Integer minimumStock,
            Long categoryId
    ) {
        Objects.requireNonNull(id, "id");
        String normalizedName = normalizeName(name);
        String normalizedSku = normalizeSku(sku);
        validateAmounts(price, cost, minimumStock);
        Objects.requireNonNull(categoryId, "categoryId");

        Product product = findById(id);
        if (!normalizedSku.equalsIgnoreCase(product.getSku())) {
            rejectDuplicateSku(normalizedSku);
        }
        Category category = findCategoryById(categoryId);

        product.update(
                normalizedName,
                normalizedSku,
                description,
                price,
                cost,
                minimumStock,
                category
        );
        return productRepository.save(product);
    }

    @Transactional
    public Product activate(Long id) {
        Objects.requireNonNull(id, "id");
        Product product = findById(id);
        product.activate();
        return productRepository.save(product);
    }

    @Transactional
    public Product deactivate(Long id) {
        Objects.requireNonNull(id, "id");
        Product product = findById(id);
        product.deactivate();
        return productRepository.save(product);
    }

    private Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Category findCategoryById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private void ensureCategoryExists(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new CategoryNotFoundException(categoryId);
        }
    }

    private void rejectDuplicateSku(String sku) {
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw new DuplicateProductSkuException(sku);
        }
    }

    private static void validateAmounts(BigDecimal price, BigDecimal cost, Integer minimumStock) {
        requireNonNegative(price, "price");
        requireNonNegative(cost, "cost");
        Objects.requireNonNull(minimumStock, "minimumStock");
        if (minimumStock < 0) {
            throw new IllegalArgumentException("minimumStock must not be negative");
        }
    }

    private static void requireNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static String normalizeName(String name) {
        String normalizedName = Objects.requireNonNull(name, "name").trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        return normalizedName;
    }

    private static String normalizeSku(String sku) {
        String normalizedSku = Objects.requireNonNull(sku, "sku").trim().toUpperCase(Locale.ROOT);
        if (normalizedSku.isEmpty()) {
            throw new IllegalArgumentException("Product SKU must not be blank");
        }
        return normalizedSku;
    }

    private static String normalizeSearchName(String name) {
        String normalizedName = Objects.requireNonNull(name, "name").trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Product name search must not be blank");
        }
        return normalizedName;
    }
}
