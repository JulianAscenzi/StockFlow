package com.julianas.stockflow.product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

final class ProductAmounts {
    private static final BigDecimal MAXIMUM = new BigDecimal("9999999999.99");

    private ProductAmounts() {
    }

    static BigDecimal normalize(BigDecimal value, String field) {
        Objects.requireNonNull(value, field);
        if (value.scale() > 2 || value.signum() < 0 || value.compareTo(MAXIMUM) > 0) {
            throw new IllegalArgumentException(field + " must be non-negative with at most 10 integer and 2 decimal digits");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }
}
