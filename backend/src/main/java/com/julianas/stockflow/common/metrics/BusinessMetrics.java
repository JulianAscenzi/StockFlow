package com.julianas.stockflow.common.metrics;

import com.julianas.stockflow.inventory.StockMovementType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumMap;
import java.util.Map;

@Component
public class BusinessMetrics {
    public enum SaleRejection { INSUFFICIENT_STOCK, INACTIVE_PRODUCT }

    private final Counter confirmedSales;
    private final Map<SaleRejection, Counter> rejectedSales = new EnumMap<>(SaleRejection.class);
    private final Map<StockMovementType, Counter> movements = new EnumMap<>(StockMovementType.class);

    public BusinessMetrics(MeterRegistry registry) {
        confirmedSales = registry.counter("stockflow.sales.confirmed");
        for (SaleRejection reason : SaleRejection.values()) {
            rejectedSales.put(reason, registry.counter("stockflow.sales.rejected", "reason", reason.name()));
        }
        for (StockMovementType type : StockMovementType.values()) {
            movements.put(type, registry.counter("stockflow.stock.movements", "type", type.name()));
        }
    }

    public void saleConfirmed() {
        afterCommit(confirmedSales);
    }

    public void stockMovement(StockMovementType type) {
        afterCommit(movements.get(type));
    }

    // A rejected attempt is an outcome of rollback, not a committed sale.
    public void saleRejected(SaleRejection reason) {
        requireTransaction();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) rejectedSales.get(reason).increment();
            }
        });
    }

    private void afterCommit(Counter counter) {
        requireTransaction();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                counter.increment();
            }
        });
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Business metrics require a managed transaction");
        }
    }
}
