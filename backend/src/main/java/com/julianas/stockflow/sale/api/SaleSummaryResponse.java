package com.julianas.stockflow.sale.api;

import java.math.BigDecimal;
import java.time.Instant;

public record SaleSummaryResponse(Long id, BigDecimal total, Instant createdAt) {}
