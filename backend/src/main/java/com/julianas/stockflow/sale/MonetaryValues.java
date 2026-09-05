package com.julianas.stockflow.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

final class MonetaryValues {

    private static final BigDecimal MAX_NUMERIC_12_2 = new BigDecimal("9999999999.99");
    private static final BigDecimal MAX_NUMERIC_14_2 = new BigDecimal("999999999999.99");

    private MonetaryValues() {
    }

    static BigDecimal numeric12_2(BigDecimal value, String fieldName) {
        return normalize(value, MAX_NUMERIC_12_2, fieldName);
    }

    static BigDecimal numeric14_2(BigDecimal value, String fieldName) {
        return normalize(value, MAX_NUMERIC_14_2, fieldName);
    }

    private static BigDecimal normalize(BigDecimal value, BigDecimal maximum, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        final BigDecimal normalized;
        try {
            normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " must have at most 2 decimal places", exception);
        }
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        if (normalized.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(fieldName + " exceeds its NUMERIC capacity");
        }
        return normalized;
    }
}
