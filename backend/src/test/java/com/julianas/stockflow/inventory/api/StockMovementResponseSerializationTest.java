package com.julianas.stockflow.inventory.api;

import com.julianas.stockflow.inventory.StockMovementType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockMovementResponseSerializationTest {

    @Test
    void serializesResponseWithSimpleProductIdEnumAndIsoInstant() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SerializationController()).build();

        mockMvc.perform(get("/stock-movement").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementType").value("IN"))
                .andExpect(jsonPath("$.productId").value(42))
                .andExpect(jsonPath("$.product").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-03-01T10:15:30Z"));
    }

    @RestController
    private static class SerializationController {

        @GetMapping("/stock-movement")
        StockMovementResponse movement() {
            return new StockMovementResponse(
                    7L, 42L, StockMovementType.IN, 5, 10, 15,
                    "Supplier delivery", Instant.parse("2026-03-01T10:15:30Z")
            );
        }
    }
}
