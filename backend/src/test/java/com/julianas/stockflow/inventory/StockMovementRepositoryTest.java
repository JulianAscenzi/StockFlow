package com.julianas.stockflow.inventory;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryRepository;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class StockMovementRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndRetrievesInMovementWithProductRelationship() {
        Product product = saveProduct("MOU-01");
        StockMovement saved = stockMovementRepository.saveAndFlush(
                new StockMovement(product, StockMovementType.IN, 5, 10, 15, "  Delivery  ")
        );
        Long movementId = saved.getId();

        assertNotNull(movementId);
        assertNotNull(saved.getCreatedAt());

        entityManager.clear();
        StockMovement found = stockMovementRepository.findById(movementId).orElseThrow();

        assertEquals(StockMovementType.IN, found.getMovementType());
        assertEquals(5, found.getQuantity());
        assertEquals(10, found.getStockBefore());
        assertEquals(15, found.getStockAfter());
        assertEquals("Delivery", found.getReason());
        assertEquals(product.getId(), found.getProduct().getId());
        assertEquals("MOU-01", found.getProduct().getSku());
    }

    @Test
    void savesValidOutMovement() {
        Product product = saveProduct("KEY-01");

        StockMovement saved = stockMovementRepository.saveAndFlush(
                new StockMovement(product, StockMovementType.OUT, 3, 10, 7, "Sale")
        );

        assertNotNull(saved.getId());
        assertEquals(StockMovementType.OUT, saved.getMovementType());
        assertEquals(7, saved.getStockAfter());
    }

    @Test
    void findsOnlyProductHistoryOrderedByCreatedAtAndIdWithPagination() {
        Product firstProduct = saveProduct("MOU-01");
        Product otherProduct = saveProduct("KEY-01");
        StockMovement first = stockMovementRepository.saveAndFlush(
                new StockMovement(firstProduct, StockMovementType.IN, 1, 0, 1, "First")
        );
        StockMovement second = stockMovementRepository.saveAndFlush(
                new StockMovement(firstProduct, StockMovementType.IN, 1, 1, 2, "Second")
        );
        stockMovementRepository.saveAndFlush(
                new StockMovement(otherProduct, StockMovementType.IN, 1, 0, 1, "Other product")
        );

        Instant sharedCreatedAt = Instant.parse("2026-01-01T00:00:00Z");
        entityManager.createNativeQuery("UPDATE stock_movements SET created_at = :createdAt WHERE id IN (:firstId, :secondId)")
                .setParameter("createdAt", sharedCreatedAt)
                .setParameter("firstId", first.getId())
                .setParameter("secondId", second.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Page<StockMovement> firstPage = stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(
                firstProduct.getId(),
                PageRequest.of(0, 1)
        );
        Page<StockMovement> secondPage = stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(
                firstProduct.getId(),
                PageRequest.of(1, 1)
        );

        assertEquals(2, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(1, firstPage.getSize());
        assertEquals(second.getId(), firstPage.getContent().getFirst().getId());
        assertEquals(first.getId(), secondPage.getContent().getFirst().getId());
        assertTrue(firstPage.getContent().stream().noneMatch(movement -> movement.getProduct().getId().equals(otherProduct.getId())));
    }

    private Product saveProduct(String sku) {
        Category category = categoryRepository.saveAndFlush(new Category("Peripherals " + sku, null));
        return productRepository.saveAndFlush(new Product(
                "Product " + sku,
                sku,
                null,
                new BigDecimal("123.45"),
                new BigDecimal("67.89"),
                10,
                2,
                true,
                category
        ));
    }
}
