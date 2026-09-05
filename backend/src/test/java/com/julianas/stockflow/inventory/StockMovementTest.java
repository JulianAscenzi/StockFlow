package com.julianas.stockflow.inventory;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.product.Product;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockMovementTest {

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
    void constructorInPreservesFields() {
        Product product = validProduct();

        StockMovement movement = new StockMovement(product, StockMovementType.IN, 5, 10, 15, "Delivery");

        assertSame(product, movement.getProduct());
        assertEquals(StockMovementType.IN, movement.getMovementType());
        assertEquals(5, movement.getQuantity());
        assertEquals(10, movement.getStockBefore());
        assertEquals(15, movement.getStockAfter());
        assertEquals("Delivery", movement.getReason());
    }

    @Test
    void constructorOutPreservesFields() {
        StockMovement movement = new StockMovement(validProduct(), StockMovementType.OUT, 4, 10, 6, "Sale");

        assertEquals(StockMovementType.OUT, movement.getMovementType());
        assertEquals(4, movement.getQuantity());
        assertEquals(10, movement.getStockBefore());
        assertEquals(6, movement.getStockAfter());
        assertEquals("Sale", movement.getReason());
    }

    @Test
    void constructorNormalizesReasonWithTrim() {
        StockMovement movement = new StockMovement(validProduct(), StockMovementType.IN, 1, 0, 1, "  Inventory count  ");

        assertEquals("Inventory count", movement.getReason());
    }

    @Test
    void rejectsNullProduct() {
        assertThrows(
                NullPointerException.class,
                () -> new StockMovement(null, StockMovementType.IN, 1, 0, 1, "Delivery")
        );
    }

    @Test
    void rejectsNullMovementType() {
        assertThrows(
                NullPointerException.class,
                () -> new StockMovement(validProduct(), null, 1, 0, 1, "Delivery")
        );
    }

    @Test
    void rejectsZeroAndNegativeQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(validProduct(), StockMovementType.IN, 0, 0, 0, "Count")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(validProduct(), StockMovementType.IN, -1, 0, 0, "Count")
        );
    }

    @Test
    void rejectsNegativeBalances() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(validProduct(), StockMovementType.IN, 1, -1, 0, "Count")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(validProduct(), StockMovementType.OUT, 1, 1, -1, "Count")
        );
    }

    @Test
    void rejectsNullBlankAndWhitespaceReason() {
        assertThrows(
                NullPointerException.class,
                () -> new StockMovement(validProduct(), StockMovementType.IN, 1, 0, 1, null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(validProduct(), StockMovementType.IN, 1, 0, 1, "")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(validProduct(), StockMovementType.IN, 1, 0, 1, "   ")
        );
    }

    @Test
    void rejectsReasonLongerThan255Characters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(validProduct(), StockMovementType.IN, 1, 0, 1, "r".repeat(256))
        );
    }

    @Test
    void rejectsIncorrectInBalance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(validProduct(), StockMovementType.IN, 5, 10, 14, "Delivery")
        );
    }

    @Test
    void rejectsIncorrectOutBalance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(validProduct(), StockMovementType.OUT, 5, 10, 6, "Sale")
        );
    }

    @Test
    void safelyRejectsBalanceThatWouldOverflowAnInteger() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StockMovement(
                        validProduct(),
                        StockMovementType.IN,
                        1,
                        Integer.MAX_VALUE,
                        0,
                        "Delivery"
                )
        );
    }

    @Test
    void beanValidationAcceptsValidMovement() {
        StockMovement movement = new StockMovement(validProduct(), StockMovementType.IN, 5, 10, 15, "Delivery");

        Set<ConstraintViolation<StockMovement>> violations = validator.validate(movement);

        assertTrue(violations.isEmpty());
    }

    private static Product validProduct() {
        return new Product(
                "Mouse",
                "MOU-01",
                null,
                new BigDecimal("123.45"),
                new BigDecimal("67.89"),
                10,
                2,
                true,
                new Category("Electronics", null)
        );
    }
}
