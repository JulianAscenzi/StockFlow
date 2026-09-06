package com.julianas.stockflow.dashboard;

import com.julianas.stockflow.product.ProductRepository;
import com.julianas.stockflow.sale.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DashboardServiceTest {
    @Test
    void usesLocalDayAndKeepsPaginationIndependentFromMetrics() {
        var sales = mock(SaleRepository.class);
        var products = mock(ProductRepository.class);
        var totals = mock(SaleRepository.SaleTotals.class);
        var items = mock(SaleRepository.ItemTotals.class);
        var start = Instant.parse("2026-09-06T03:00:00Z");
        var end = Instant.parse("2026-09-07T03:00:00Z");
        when(sales.dailySales(start, end)).thenReturn(totals);
        when(sales.dailyItems(start, end)).thenReturn(items);
        var page = PageRequest.of(2, 5);
        when(products.findLowStock(page)).thenReturn(Page.empty(page));
        var service = new DashboardService(sales, products,
                Clock.fixed(Instant.parse("2026-09-07T02:59:59Z"), ZoneOffset.UTC));

        assertEquals("2026-09-06", service.today(page).date().toString());
        verify(sales).dailySales(start, end);
        verify(sales).dailyItems(start, end);
        verify(products).findLowStock(page);
    }
}
