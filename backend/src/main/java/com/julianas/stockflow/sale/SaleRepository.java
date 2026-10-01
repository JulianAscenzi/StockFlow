package com.julianas.stockflow.sale;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Page<Sale> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Query("select s from Sale s left join fetch s.items where s.id = :id")
    Optional<Sale> findDetailById(@Param("id") Long id);

    @Query("select count(s) as saleCount, coalesce(sum(s.total), 0) as revenue from Sale s where s.createdAt >= :start and s.createdAt < :end")
    SaleTotals dailySales(@Param("start") Instant start, @Param("end") Instant end);

    @Query("select coalesce(sum(i.quantity), 0) as unitsSold, coalesce(sum(i.subtotal - i.unitCost * i.quantity), 0) as grossProfit from SaleItem i where i.sale.createdAt >= :start and i.sale.createdAt < :end")
    ItemTotals dailyItems(@Param("start") Instant start, @Param("end") Instant end);

    interface SaleTotals {
        long getSaleCount();
        java.math.BigDecimal getRevenue();
    }

    interface ItemTotals {
        long getUnitsSold();
        java.math.BigDecimal getGrossProfit();
    }
}
