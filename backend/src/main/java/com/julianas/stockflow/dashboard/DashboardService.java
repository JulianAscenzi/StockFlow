package com.julianas.stockflow.dashboard;

import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.product.ProductRepository;
import com.julianas.stockflow.sale.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

@Service
public class DashboardService {
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private final SaleRepository sales;
    private final ProductRepository products;
    private final Clock clock;

    @Autowired
    public DashboardService(SaleRepository sales, ProductRepository products) {
        this(sales, products, Clock.systemUTC());
    }

    DashboardService(SaleRepository sales, ProductRepository products, Clock clock) {
        this.sales = Objects.requireNonNull(sales);
        this.products = Objects.requireNonNull(products);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResponse today(Pageable pageable) {
        LocalDate date = LocalDate.now(clock.withZone(ZONE));
        var start = date.atStartOfDay(ZONE).toInstant();
        var end = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        var totals = sales.dailySales(start, end);
        var items = sales.dailyItems(start, end);
        var lowStock = products.findLowStock(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(p -> new LowStockProduct(p.getId(), p.getName(), p.getSku(), p.getStock(), p.getMinimumStock()));
        return new DashboardResponse(date, ZONE.getId(), totals.getSaleCount(), totals.getRevenue(),
                items.getUnitsSold(), items.getGrossProfit(), PageResponse.from(lowStock));
    }
}
