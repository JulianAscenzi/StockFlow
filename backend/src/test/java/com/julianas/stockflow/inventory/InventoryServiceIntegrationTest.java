package com.julianas.stockflow.inventory;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryRepository;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductNotFoundException;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class InventoryServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired private InventoryService inventoryService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private StockMovementRepository stockMovementRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE stock_movements, products, categories RESTART IDENTITY");
    }

    @Test
    void movementsUpdateStockAndRecordHistory() {
        Product product = saveProduct(10);

        StockMovement in = inventoryService.increaseStock(product.getId(), 5, "  Delivery  ");
        StockMovement out = inventoryService.decreaseStock(product.getId(), 3, "Sale");

        assertEquals(12, productRepository.findById(product.getId()).orElseThrow().getStock());
        assertEquals(StockMovementType.IN, in.getMovementType());
        assertEquals(10, in.getStockBefore());
        assertEquals(15, in.getStockAfter());
        assertEquals(5, in.getQuantity());
        assertEquals("Delivery", in.getReason());
        assertNotNull(in.getCreatedAt());
        assertEquals(StockMovementType.OUT, out.getMovementType());
        assertEquals(15, out.getStockBefore());
        assertEquals(12, out.getStockAfter());
        assertEquals(2, stockMovementRepository.count());
    }

    @Test
    void failedMovementsRollbackStockAndHistory() {
        Product product = saveProduct(3);

        assertThrows(InsufficientStockException.class, () -> inventoryService.decreaseStock(product.getId(), 4, "Sale"));
        assertEquals(3, productRepository.findById(product.getId()).orElseThrow().getStock());
        assertEquals(0, stockMovementRepository.count());

        Product maximum = saveProduct(Integer.MAX_VALUE);
        assertThrows(StockLimitExceededException.class, () -> inventoryService.increaseStock(maximum.getId(), 1, "Count"));
        assertEquals(Integer.MAX_VALUE, productRepository.findById(maximum.getId()).orElseThrow().getStock());
        assertEquals(0, stockMovementRepository.count());
    }

    @Test
    void historyIsPagedAndMissingProductFails() {
        Product product = saveProduct(0);
        inventoryService.increaseStock(product.getId(), 1, "First");
        inventoryService.increaseStock(product.getId(), 1, "Second");

        List<StockMovement> history = inventoryService.getHistory(product.getId(), org.springframework.data.domain.PageRequest.of(0, 1)).getContent();
        assertEquals(1, history.size());
        assertEquals("Second", history.getFirst().getReason());
        assertEquals(2, inventoryService.getHistory(product.getId(), org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements());
        assertThrows(ProductNotFoundException.class,
                () -> inventoryService.getHistory(999L, org.springframework.data.domain.PageRequest.of(0, 1)));
    }

    @Test
    void concurrentWithdrawalsAllowExactlyOneWinner() throws Exception {
        Product product = saveProduct(0);
        inventoryService.increaseStock(product.getId(), 10, "Initial load");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> withdrawWhenStarted(product.getId(), ready, start));
            Future<?> second = executor.submit(() -> withdrawWhenStarted(product.getId(), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            int successes = completedWithdrawals(first, second);

            assertEquals(1, successes);
            assertEquals(3, productRepository.findById(product.getId()).orElseThrow().getStock());
            List<StockMovement> movements = stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(
                    product.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
            assertEquals(2, movements.size());
            assertEquals(1, movements.stream().filter(movement -> movement.getMovementType() == StockMovementType.IN
                    && movement.getQuantity() == 10).count());
            assertEquals(1, movements.stream().filter(movement -> movement.getMovementType() == StockMovementType.OUT
                    && movement.getQuantity() == 7).count());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private int completedWithdrawals(Future<?>... futures) throws Exception {
        int successes = 0;
        int insufficient = 0;
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
                successes++;
            } catch (ExecutionException exception) {
                assertInstanceOf(InsufficientStockException.class, exception.getCause());
                insufficient++;
            }
        }
        assertEquals(1, insufficient);
        return successes;
    }

    private void withdrawWhenStarted(Long productId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
        inventoryService.decreaseStock(productId, 7, "Concurrent sale");
    }

    private Product saveProduct(int stock) {
        Category category = categoryRepository.saveAndFlush(new Category("Peripherals " + System.nanoTime(), null));
        return productRepository.saveAndFlush(new Product("Mouse", "SKU-" + System.nanoTime(), null,
                new BigDecimal("10.00"), new BigDecimal("5.00"), stock, 0, true, category));
    }
}
