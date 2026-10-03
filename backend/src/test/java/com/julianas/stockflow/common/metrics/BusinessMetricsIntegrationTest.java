package com.julianas.stockflow.common.metrics;

import com.julianas.stockflow.api.ApiIntegrationTestSupport;
import com.julianas.stockflow.category.CategoryService;
import com.julianas.stockflow.inventory.InsufficientStockException;
import com.julianas.stockflow.inventory.InventoryService;
import com.julianas.stockflow.product.ProductService;
import com.julianas.stockflow.sale.IdempotentSaleService;
import com.julianas.stockflow.sale.InactiveProductException;
import com.julianas.stockflow.sale.SaleService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessMetricsIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired private MeterRegistry registry;
    @Autowired private CategoryService categories;
    @Autowired private ProductService products;
    @Autowired private InventoryService inventory;
    @Autowired private SaleService sales;
    @Autowired private IdempotentSaleService idempotentSales;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void successIsInvisibleBeforeCommitAndCountsSaleAndEachMovementAfterwards() {
        long product = product();
        double confirmed = confirmed();
        double incoming = movement("IN");
        double outgoing = movement("OUT");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            inventory.increaseStock(product, 5, "Private arbitrary reason");
            sales.confirm(null, lines(product, 2));
            assertThat(confirmed()).isEqualTo(confirmed);
            assertThat(movement("IN")).isEqualTo(incoming);
            assertThat(movement("OUT")).isEqualTo(outgoing);
        });
        assertThat(confirmed()).isEqualTo(confirmed + 1);
        assertThat(movement("IN")).isEqualTo(incoming + 1);
        assertThat(movement("OUT")).isEqualTo(outgoing + 1);
    }

    @Test
    void idempotentReplayReturnsSameSaleWithoutCountingSaleOrMovementTwice() {
        long product = product();
        inventory.increaseStock(product, 5, "Initial stock");
        double confirmed = confirmed();
        double outgoing = movement("OUT");
        String key = UUID.randomUUID().toString();
        var first = idempotentSales.confirm(key, null, lines(product, 2));
        var replay = idempotentSales.confirm(key, null, lines(product, 2));
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(confirmed()).isEqualTo(confirmed + 1);
        assertThat(movement("OUT")).isEqualTo(outgoing + 1);
    }

    @Test
    void partialSaleRollbackCountsOneRejectionButNoSuccessfulMovementOrSale() {
        long available = product();
        long unavailable = product();
        inventory.increaseStock(available, 5, "Initial stock");
        double confirmed = confirmed();
        double outgoing = movement("OUT");
        double rejected = rejected("INSUFFICIENT_STOCK");
        assertThatThrownBy(() -> sales.confirm(null, List.of(new SaleService.SaleLine(available, 1),
                new SaleService.SaleLine(unavailable, 1)))).isInstanceOf(InsufficientStockException.class);
        assertThat(confirmed()).isEqualTo(confirmed);
        assertThat(movement("OUT")).isEqualTo(outgoing);
        assertThat(rejected("INSUFFICIENT_STOCK")).isEqualTo(rejected + 1);
        assertThat(products.getById(available).getStock()).isEqualTo(5);
    }

    @Test
    void outerTransactionRollbackDiscardsSuccessfulIdempotentSaleAndInventoryEvents() {
        long product = product();
        inventory.increaseStock(product, 5, "Initial stock");
        double confirmed = confirmed();
        double outgoing = movement("OUT");
        String key = UUID.randomUUID().toString();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            idempotentSales.confirm(key, null, lines(product, 1));
            status.setRollbackOnly();
        });
        assertThat(confirmed()).isEqualTo(confirmed);
        assertThat(movement("OUT")).isEqualTo(outgoing);
        assertThat(jdbc.queryForObject("select count(*) from sale_confirmations", Integer.class)).isZero();
        idempotentSales.confirm(key, null, lines(product, 1));
        assertThat(confirmed()).isEqualTo(confirmed + 1);
    }

    @Test
    void laterPersistenceFailureDiscardsScheduledSuccessCounters() {
        long product = product();
        double incoming = movement("IN");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            inventory.increaseStock(product, 5, "Initial stock");
            jdbc.update("update products set stock = -1 where id = ?", product);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(movement("IN")).isEqualTo(incoming);
        assertThat(products.getById(product).getStock()).isZero();
    }

    @Test
    void inactiveProductIsRejectedAndMissingProductDoesNotCreateStockRejection() {
        long product = product();
        products.deactivate(product);
        double rejected = rejected("INACTIVE_PRODUCT");
        assertThatThrownBy(() -> sales.confirm(null, lines(product, 1))).isInstanceOf(InactiveProductException.class);
        assertThat(rejected("INACTIVE_PRODUCT")).isEqualTo(rejected + 1);
        double missingRejections = rejected("INSUFFICIENT_STOCK");
        assertThatThrownBy(() -> sales.confirm(null, lines(Long.MAX_VALUE, 1)))
                .isInstanceOf(com.julianas.stockflow.product.ProductNotFoundException.class);
        assertThat(rejected("INSUFFICIENT_STOCK")).isEqualTo(missingRejections);
    }

    private long product() {
        String suffix = UUID.randomUUID().toString();
        long category = categories.create("Category " + suffix, null).getId();
        return products.create("Product " + suffix, suffix, null, BigDecimal.TEN, BigDecimal.ONE, 0, category).getId();
    }

    private List<SaleService.SaleLine> lines(long product, int quantity) {
        return List.of(new SaleService.SaleLine(product, quantity));
    }
    private double confirmed() { return registry.get("stockflow.sales.confirmed").counter().count(); }
    private double movement(String type) { return registry.get("stockflow.stock.movements").tag("type", type).counter().count(); }
    private double rejected(String reason) { return registry.get("stockflow.sales.rejected").tag("reason", reason).counter().count(); }
}
