package com.julianas.stockflow.dashboard;

import com.julianas.stockflow.common.api.PageResponse;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardResponse(
        LocalDate date, String timeZone, long saleCount, BigDecimal revenue,
        long unitsSold, BigDecimal grossProfit, PageResponse<LowStockProduct> lowStockProducts
) {
}
