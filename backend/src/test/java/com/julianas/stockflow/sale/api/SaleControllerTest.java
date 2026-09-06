package com.julianas.stockflow.sale.api;

import com.julianas.stockflow.common.error.GlobalExceptionHandler;
import com.julianas.stockflow.sale.Sale;
import com.julianas.stockflow.sale.SaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SaleController.class)
@Import(GlobalExceptionHandler.class)
class SaleControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private SaleService saleService;
    @MockitoBean private SaleMapper saleMapper;

    @Test
    void confirmsSaleWithValidatedContract() throws Exception {
        Sale sale = mock(Sale.class);
        SaleResponse response = new SaleResponse(12L, new BigDecimal("25.00"), "Counter sale",
                Instant.parse("2026-03-01T10:15:30Z"), List.of());
        when(saleService.confirm("Counter sale", List.of(new SaleService.SaleLine(8L, 2)))).thenReturn(sale);
        when(saleMapper.toResponse(sale)).thenReturn(response);

        mockMvc.perform(post("/api/sales").contentType("application/json")
                        .content("{\"notes\":\"Counter sale\",\"items\":[{\"productId\":8,\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/sales/12"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.total").value(25));

        verify(saleService).confirm("Counter sale", List.of(new SaleService.SaleLine(8L, 2)));
        verify(saleMapper).toResponse(sale);
    }

    @Test
    void rejectsInvalidPayloadWithoutCallingDependencies() throws Exception {
        mockMvc.perform(post("/api/sales").contentType("application/json")
                        .content("{\"items\":[{\"productId\":0,\"quantity\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        verifyNoInteractions(saleService, saleMapper);
    }

    @Test
    void rejectsEmptySaleRequestWithoutCallingDependencies() throws Exception {
        mockMvc.perform(post("/api/sales").contentType("application/json")
                        .content("{\"notes\":\"Counter sale\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(saleService, saleMapper);
    }
}
