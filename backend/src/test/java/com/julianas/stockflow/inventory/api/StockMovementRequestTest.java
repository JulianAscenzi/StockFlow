package com.julianas.stockflow.inventory.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockMovementRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        assertTrue(validate(new StockMovementRequest(3, "Stock reception")).isEmpty());
    }

    @Test
    void rejectsNullQuantity() {
        assertViolationProperties(new StockMovementRequest(null, "Stock reception"), "quantity");
    }

    @Test
    void rejectsZeroQuantity() {
        assertViolationProperties(new StockMovementRequest(0, "Stock reception"), "quantity");
    }

    @Test
    void rejectsNegativeQuantity() {
        assertViolationProperties(new StockMovementRequest(-1, "Stock reception"), "quantity");
    }

    @Test
    void rejectsNullReason() {
        assertViolationProperties(new StockMovementRequest(1, null), "reason");
    }

    @Test
    void rejectsEmptyReason() {
        assertViolationProperties(new StockMovementRequest(1, ""), "reason");
    }

    @Test
    void rejectsWhitespaceOnlyReason() {
        assertViolationProperties(new StockMovementRequest(1, "   "), "reason");
    }

    @Test
    void rejectsReasonThatIsTooLong() {
        assertViolationProperties(new StockMovementRequest(1, "r".repeat(256)), "reason");
    }

    @Test
    void acceptsReasonWith255Characters() {
        assertTrue(validate(new StockMovementRequest(1, "r".repeat(255))).isEmpty());
    }

    private static Set<ConstraintViolation<StockMovementRequest>> validate(StockMovementRequest request) {
        return validator.validate(request);
    }

    private static void assertViolationProperties(StockMovementRequest request, String... properties) {
        assertEquals(
                Set.of(properties),
                validate(request).stream()
                        .map(violation -> violation.getPropertyPath().toString())
                        .collect(java.util.stream.Collectors.toSet())
        );
    }
}
