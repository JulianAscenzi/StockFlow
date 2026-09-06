package com.julianas.stockflow.dashboard;

import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@Import(GlobalExceptionHandler.class)
class DashboardControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private DashboardService service;

    @Test
    void exposesMetricsAndPagedLowStock() throws Exception {
        when(service.today(any())).thenReturn(new DashboardResponse(LocalDate.of(2026, 9, 6),
                "America/Argentina/Buenos_Aires", 2, new BigDecimal("50.00"), 6, new BigDecimal("10.00"),
                new PageResponse<>(List.of(new LowStockProduct(3L, "Mouse", "MOU", 1, 2)),
                        1, 1, 2, 2, false, true)));
        mvc.perform(get("/api/dashboard?page=1&size=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-09-06"))
                .andExpect(jsonPath("$.saleCount").value(2))
                .andExpect(jsonPath("$.revenue").value(50))
                .andExpect(jsonPath("$.unitsSold").value(6))
                .andExpect(jsonPath("$.grossProfit").value(10))
                .andExpect(jsonPath("$.lowStockProducts.content[0].sku").value("MOU"))
                .andExpect(jsonPath("$.lowStockProducts.totalElements").value(2));
        verify(service).today(PageRequest.of(1, 1));
    }
}
