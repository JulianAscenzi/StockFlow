package com.julianas.stockflow.dashboard;

import com.julianas.stockflow.api.ApiIntegrationTestSupport;
import com.julianas.stockflow.product.ProductRepository;
import com.julianas.stockflow.sale.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardIntegrationTest extends ApiIntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SaleRepository sales;
    @Autowired private ProductRepository products;
    @Autowired private MockMvc mvc;

    @Test
    void aggregatesSalesOnceAndUsesSnapshotsWithinArgentinaDay() {
        // UTC September 7, but still September 6 in Argentina.
        var service = new DashboardService(sales, products,
                Clock.fixed(Instant.parse("2026-09-07T02:00:00Z"), ZoneOffset.UTC));
        long first = product("First", 1, 0);
        long second = product("Second", 2, 0);
        sale("2026-09-06T03:00:00Z", first, second);
        sale("2026-09-07T02:59:59Z", first, second);
        sale("2026-09-06T02:59:59Z", first, second);
        sale("2026-09-07T03:00:00Z", first, second);

        var result = service.today(PageRequest.of(0, 20));

        assertEquals("2026-09-06", result.date().toString());
        assertEquals(2, result.saleCount());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.revenue()));
        assertEquals(6, result.unitsSold());
        assertEquals(0, new BigDecimal("10.00").compareTo(result.grossProfit()));
    }

    @Test
    void filtersBeforePagingAndIncludesThresholdEquality() {
        product("Healthy", 0, 0);
        jdbc.update("UPDATE products SET stock = 1 WHERE name = 'Healthy'");
        long first = product("Low A", 10, 10);
        long second = product("Low B", 11, 20);
        var service = new DashboardService(sales, products);

        var page = service.today(PageRequest.of(0, 1)).lowStockProducts();
        assertEquals(2, page.totalElements());
        assertEquals(first, page.content().getFirst().id());
        assertEquals(second, service.today(PageRequest.of(1, 1)).lowStockProducts().content().getFirst().id());
    }

    @Test
    void emptyDashboardIsAvailableThroughHttpWithZeroMetrics() throws Exception {
        mvc.perform(get("/api/dashboard").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("America/Argentina/Buenos_Aires"))
                .andExpect(jsonPath("$.saleCount").value(0))
                .andExpect(jsonPath("$.revenue").value(0))
                .andExpect(jsonPath("$.unitsSold").value(0))
                .andExpect(jsonPath("$.grossProfit").value(0))
                .andExpect(jsonPath("$.lowStockProducts.totalElements").value(0))
                .andExpect(jsonPath("$.lowStockProducts.size").value(5));
    }

    private long product(String name, int stock, int minimum) {
        long category = jdbc.queryForObject("INSERT INTO categories(name) VALUES (?) RETURNING id", Long.class, name);
        return jdbc.queryForObject("""
                INSERT INTO products(name, sku, price, cost, stock, minimum_stock, category_id)
                VALUES (?, ?, 999, 888, ?, ?, ?) RETURNING id
                """, Long.class, name, name, stock, minimum, category);
    }

    private void sale(String timestamp, long first, long second) {
        long id = jdbc.queryForObject("INSERT INTO sales(total, created_at) VALUES (25, ?) RETURNING id",
                Long.class, Timestamp.from(Instant.parse(timestamp)));
        jdbc.update("""
                INSERT INTO sale_items(sale_id, product_id, product_name, product_sku, quantity, unit_price, unit_cost, subtotal)
                VALUES (?, ?, 'Snapshot A', 'A', 2, 10, 6, 20), (?, ?, 'Snapshot B', 'B', 1, 5, 8, 5)
                """, id, first, id, second);
    }
}
