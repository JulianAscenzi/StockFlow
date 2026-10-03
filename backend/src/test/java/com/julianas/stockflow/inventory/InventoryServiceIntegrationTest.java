package com.julianas.stockflow.inventory;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryRepository;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductNotFoundException;
import com.julianas.stockflow.product.ProductRepository;
import com.julianas.stockflow.product.ProductService;
import com.julianas.stockflow.sale.SaleService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired private ProductService productService;
    @Autowired private SaleService saleService;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE sale_confirmations, sale_items, sales, stock_movements, products, categories RESTART IDENTITY");
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

    @ParameterizedTest
    @EnumSource(ConcurrentOperation.class)
    void concurrentOperationsPreserveCommittedStock(ConcurrentOperation change) throws Exception {
        Product product = saveProduct(10);
        if (change == ConcurrentOperation.ACTIVATE) productService.deactivate(product.getId());
        Long categoryId = jdbcTemplate.queryForObject(
                "select category_id from products where id = ?", Long.class, product.getId());
        CountDownLatch stockChanged = new CountDownLatch(1);
        CountDownLatch releaseStock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> movement = executor.submit(() ->
                    new TransactionTemplate(transactionManager)
                            .executeWithoutResult(status -> {
                                inventoryService.decreaseStock(product.getId(), 3, "Concurrent withdrawal");
                                stockChanged.countDown();
                                awaitLatch(releaseStock);
                            }));
            assertTrue(stockChanged.await(5, TimeUnit.SECONDS));
            Future<?> edit = executor.submit(() -> {
                switch (change) {
                    case UPDATE -> productService.update(product.getId(), "Updated mouse", product.getSku(), null,
                            new BigDecimal("12.00"), new BigDecimal("5.00"), 2, categoryId);
                    case ACTIVATE -> productService.activate(product.getId());
                    case DEACTIVATE -> productService.deactivate(product.getId());
                    case SALE -> saleService.confirm(null, List.of(
                            new SaleService.SaleLine(product.getId(), 8)));
                }
            });
            // Observe the real database wait instead of guessing scheduling with a sleep.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean waiting = false;
            while (System.nanoTime() < deadline && !waiting) {
                waiting = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                        "select exists(select 1 from pg_stat_activity where datname = current_database() "
                                + "and pid <> pg_backend_pid() and wait_event_type = 'Lock' "
                                + "and query ilike '%products%')", Boolean.class));
            }
            assertTrue(waiting, "Operation must wait for the inventory transaction");
            releaseStock.countDown();
            movement.get(10, TimeUnit.SECONDS);
            if (change == ConcurrentOperation.SALE) {
                ExecutionException failure = assertThrows(ExecutionException.class,
                        () -> edit.get(10, TimeUnit.SECONDS));
                assertInstanceOf(InsufficientStockException.class, failure.getCause());
            } else {
                edit.get(10, TimeUnit.SECONDS);
            }
            Product updated = productRepository.findById(product.getId()).orElseThrow();
            assertEquals(7, updated.getStock(), "Operations must not restore stale stock");
            assertEquals(change != ConcurrentOperation.DEACTIVATE, updated.isActive());
            if (change == ConcurrentOperation.UPDATE) assertEquals("Updated mouse", updated.getName());
            assertEquals(1, stockMovementRepository.count());
            StockMovement history = inventoryService.getHistory(product.getId(),
                    org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();
            assertEquals(10, history.getStockBefore());
            assertEquals(7, history.getStockAfter());
        } finally {
            releaseStock.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    enum ConcurrentOperation { UPDATE, ACTIVATE, DEACTIVATE, SALE }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out awaiting release");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
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
