package com.julianas.stockflow.sale;

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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SaleRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired private SaleRepository saleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsSaleItemsSnapshotsAndRelationshipsWithoutChangingStock() {
        Product mouse = saveProduct("Mouse", "MOU-01", "12.50", "7.25", 9);
        Product keyboard = saveProduct("Keyboard", "KEY-01", "20.00", "10.00", 4);
        Sale sale = new Sale("  counter sale  ");
        sale.addItem(mouse, 2);
        sale.addItem(keyboard, 1);

        Sale saved = saleRepository.saveAndFlush(sale);
        Long saleId = saved.getId();
        entityManager.clear();

        Sale found = saleRepository.findById(saleId).orElseThrow();
        List<SaleItem> items = found.getItems();

        assertNotNull(found.getId());
        assertNotNull(found.getCreatedAt());
        assertEquals(new BigDecimal("45.00"), found.getTotal());
        assertEquals("counter sale", found.getNotes());
        assertEquals(2, items.size());
        assertTrue(items.stream().allMatch(item -> item.getId() != null));
        SaleItem mouseItem = items.stream().filter(item -> item.getProduct().getId().equals(mouse.getId())).findFirst().orElseThrow();
        assertEquals(found.getId(), mouseItem.getSale().getId());
        assertEquals(mouse.getId(), mouseItem.getProduct().getId());
        assertEquals("Mouse", mouseItem.getProductName());
        assertEquals("MOU-01", mouseItem.getProductSku());
        assertEquals(new BigDecimal("12.50"), mouseItem.getUnitPrice());
        assertEquals(new BigDecimal("7.25"), mouseItem.getUnitCost());
        assertEquals(new BigDecimal("25.00"), mouseItem.getSubtotal());
        assertEquals(9, productRepository.findById(mouse.getId()).orElseThrow().getStock());
        assertEquals(4, productRepository.findById(keyboard.getId()).orElseThrow().getStock());
    }

    @Test
    void retainsSnapshotsWhenProductChangesLater() {
        Product product = saveProduct("Mouse", "MOU-01", "12.50", "7.25", 9);
        Sale sale = new Sale(null);
        sale.addItem(product, 1);
        Long saleId = saleRepository.saveAndFlush(sale).getId();

        product.update("Updated mouse", "MOU-02", null, new BigDecimal("99.99"), new BigDecimal("55.55"), 0, product.getCategory());
        productRepository.saveAndFlush(product);
        entityManager.clear();

        SaleItem item = saleRepository.findById(saleId).orElseThrow().getItems().getFirst();
        assertEquals("Mouse", item.getProductName());
        assertEquals("MOU-01", item.getProductSku());
        assertEquals(new BigDecimal("12.50"), item.getUnitPrice());
        assertEquals(new BigDecimal("7.25"), item.getUnitCost());
    }

    @Test
    void paginatesSalesByCreatedAtDescendingThenIdDescending() {
        Product product = saveProduct("Mouse", "MOU-01", "1.00", "0.50", 9);
        Sale oldest = saveSale(product, 1);
        Sale firstNewest = saveSale(product, 2);
        Sale secondNewest = saveSale(product, 3);
        Instant oldestTime = Instant.parse("2026-01-01T00:00:00Z");
        Instant newestTime = Instant.parse("2026-01-02T00:00:00Z");
        updateCreatedAt(oldest.getId(), oldestTime);
        updateCreatedAt(firstNewest.getId(), newestTime);
        updateCreatedAt(secondNewest.getId(), newestTime);
        entityManager.clear();

        Page<Sale> firstPage = saleRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, 2));
        Page<Sale> secondPage = saleRepository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(1, 2));

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(2, firstPage.getContent().size());
        assertEquals(secondNewest.getId(), firstPage.getContent().getFirst().getId());
        assertEquals(firstNewest.getId(), firstPage.getContent().get(1).getId());
        assertEquals(1, secondPage.getContent().size());
        assertEquals(oldest.getId(), secondPage.getContent().getFirst().getId());
    }

    @Test
    void flywayHasAppliedAllThreeMigrations() {
        Number migrations = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true AND version IN ('1', '2', '3')"
        ).getSingleResult();

        assertEquals(3L, migrations.longValue());
    }

    private Sale saveSale(Product product, int quantity) {
        Sale sale = new Sale(null);
        sale.addItem(product, quantity);
        return saleRepository.saveAndFlush(sale);
    }

    private Product saveProduct(String name, String sku, String price, String cost, int stock) {
        Category category = categoryRepository.saveAndFlush(new Category("Category " + sku, null));
        return productRepository.saveAndFlush(new Product(
                name, sku, null, new BigDecimal(price), new BigDecimal(cost), stock, 0, true, category
        ));
    }

    private void updateCreatedAt(Long saleId, Instant createdAt) {
        entityManager.createNativeQuery("UPDATE sales SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", Timestamp.from(createdAt))
                .setParameter("id", saleId)
                .executeUpdate();
    }
}
