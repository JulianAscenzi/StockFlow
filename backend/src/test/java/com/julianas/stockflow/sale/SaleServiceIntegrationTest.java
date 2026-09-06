package com.julianas.stockflow.sale;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryRepository;
import com.julianas.stockflow.inventory.StockMovementRepository;
import com.julianas.stockflow.inventory.StockMovementType;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class SaleServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired private SaleService saleService;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE sale_items, sales, stock_movements, products, categories RESTART IDENTITY");
    }

    @Test
    void confirmsSaleAndDecreasesStockInTheSameTransaction() {
        Product product = saveProduct();
        Sale sale = new Sale("Counter sale");
        sale.addItem(product, 2);

        Sale confirmed = saleService.confirm(sale);

        assertNotNull(confirmed.getId());
        assertEquals(new BigDecimal("25.00"), confirmed.getTotal());
        assertEquals(1, saleRepository.count());
        assertEquals(0, productRepository.findById(product.getId()).orElseThrow().getStock());
        var movement = stockMovementRepository
                .findByProductIdOrderByCreatedAtDescIdDesc(product.getId(), org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().getFirst();
        assertEquals(1, stockMovementRepository.count());
        assertEquals(StockMovementType.OUT, movement.getMovementType());
        assertEquals(2, movement.getQuantity());
        assertEquals(2, movement.getStockBefore());
        assertEquals(0, movement.getStockAfter());
        assertEquals("Sale", movement.getReason());
    }

    @Test
    void doesNotPersistEmptySale() {
        assertThrows(EmptySaleException.class, () -> saleService.confirm(new Sale(null)));

        assertEquals(0, saleRepository.count());
    }

    @Test
    void rollsBackTheSaleAndPreviousStockChangesWhenAnyItemHasInsufficientStock() {
        Product available = saveProduct("Available", "AVL-01", 2);
        Product insufficient = saveProduct("Insufficient", "INS-01", 1);
        Sale sale = new Sale(null);
        sale.addItem(available, 1);
        sale.addItem(insufficient, 2);

        assertThrows(com.julianas.stockflow.inventory.InsufficientStockException.class, () -> saleService.confirm(sale));

        assertEquals(0, saleRepository.count());
        assertEquals(2, productRepository.findById(available.getId()).orElseThrow().getStock());
        assertEquals(1, productRepository.findById(insufficient.getId()).orElseThrow().getStock());
        assertEquals(0, stockMovementRepository.count());
    }

    @Test
    void concurrentSalesWithOppositeLineOrdersCompleteWithoutDeadlocking() throws Exception {
        Product firstProduct = saveProduct("Keyboard", "KEY-01", 2);
        Product secondProduct = saveProduct("Mouse", "MOU-02", 2);
        Sale firstSale = new Sale(null);
        firstSale.addItem(secondProduct, 1);
        firstSale.addItem(firstProduct, 1);
        Sale secondSale = new Sale(null);
        secondSale.addItem(firstProduct, 1);
        secondSale.addItem(secondProduct, 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Sale> firstConfirmation = executor.submit(() -> confirmWhenStarted(firstSale, ready, start));
            Future<Sale> secondConfirmation = executor.submit(() -> confirmWhenStarted(secondSale, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertNotNull(firstConfirmation.get(10, TimeUnit.SECONDS).getId());
            assertNotNull(secondConfirmation.get(10, TimeUnit.SECONDS).getId());
            assertEquals(0, productRepository.findById(firstProduct.getId()).orElseThrow().getStock());
            assertEquals(0, productRepository.findById(secondProduct.getId()).orElseThrow().getStock());
            assertEquals(2, saleRepository.count());
            assertEquals(4, stockMovementRepository.count());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Sale confirmWhenStarted(Sale sale, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        return saleService.confirm(sale);
    }

    private Product saveProduct() {
        return saveProduct("Mouse", "MOU-01", 2);
    }

    private Product saveProduct(String name, String sku, int stock) {
        Category category = categoryRepository.saveAndFlush(new Category("Peripherals " + sku, null));
        return productRepository.saveAndFlush(new Product(
                name, sku, null, new BigDecimal("12.50"), new BigDecimal("7.25"), stock, 0, true, category
        ));
    }
}
