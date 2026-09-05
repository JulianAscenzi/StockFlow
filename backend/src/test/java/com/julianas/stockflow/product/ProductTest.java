package com.julianas.stockflow.product;

import com.julianas.stockflow.category.Category;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductTest {

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
    void constructorNormalizesSkuWithTrimAndUppercase() {
        Product product = validProduct("  sku-a1  ");

        assertEquals("SKU-A1", product.getSku());
    }

    @Test
    void updateNormalizesSkuAgain() {
        Product product = validProduct("SKU-A1");

        product.update(
                "Keyboard",
                "  updated-sku  ",
                "Mechanical keyboard",
                new BigDecimal("120.00"),
                new BigDecimal("80.00"),
                3,
                new Category("Peripherals", null)
        );

        assertEquals("UPDATED-SKU", product.getSku());
    }

    @Test
    void updateChangesEditableFieldsAndPreservesStock() {
        Product product = validProduct("SKU-A1");
        Category updatedCategory = new Category("Peripherals", "Computer peripherals");

        product.update(
                "Keyboard",
                "KEY-01",
                "Mechanical keyboard",
                new BigDecimal("120.50"),
                new BigDecimal("80.25"),
                3,
                updatedCategory
        );

        assertEquals("Keyboard", product.getName());
        assertEquals("KEY-01", product.getSku());
        assertEquals("Mechanical keyboard", product.getDescription());
        assertEquals(new BigDecimal("120.50"), product.getPrice());
        assertEquals(new BigDecimal("80.25"), product.getCost());
        assertEquals(3, product.getMinimumStock());
        assertSame(updatedCategory, product.getCategory());
        assertEquals(10, product.getStock());
    }

    @Test
    void activateAndDeactivateChangeActiveState() {
        Product product = validProduct("SKU-A1");

        product.deactivate();
        assertFalse(product.isActive());

        product.activate();
        assertTrue(product.isActive());
    }

    @Test
    void validationRejectsBlankNameAndSku() {
        Product product = productWith(" ", "   ", BigDecimal.ONE, BigDecimal.ONE, 0, 0, validCategory());

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertTrue(hasViolationFor(violations, "name"));
        assertTrue(hasViolationFor(violations, "sku"));
    }

    @Test
    void validationRejectsNegativePriceAndCost() {
        Product product = productWith(
                "Mouse",
                "SKU-A1",
                new BigDecimal("-0.01"),
                new BigDecimal("-1.00"),
                0,
                0,
                validCategory()
        );

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertTrue(hasViolationFor(violations, "price"));
        assertTrue(hasViolationFor(violations, "cost"));
    }

    @Test
    void validationRejectsNegativeStockAndMinimumStock() {
        Product product = productWith(
                "Mouse",
                "SKU-A1",
                BigDecimal.ONE,
                BigDecimal.ONE,
                -1,
                -2,
                validCategory()
        );

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertTrue(hasViolationFor(violations, "stock"));
        assertTrue(hasViolationFor(violations, "minimumStock"));
    }

    @Test
    void validationRejectsNullCategory() {
        Product product = productWith("Mouse", "SKU-A1", BigDecimal.ONE, BigDecimal.ONE, 0, 0, null);

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertTrue(hasViolationFor(violations, "category"));
    }

    @Test
    void validProductHasNoViolations() {
        Product product = validProduct("SKU-A1");

        Set<ConstraintViolation<Product>> violations = validator.validate(product);

        assertTrue(violations.isEmpty());
    }

    @Test
    void bigDecimalValuesPreserveTheirDecimals() {
        BigDecimal price = new BigDecimal("123.45");
        BigDecimal cost = new BigDecimal("67.89");
        Product product = productWith("Mouse", "SKU-A1", price, cost, 10, 2, validCategory());

        assertEquals(price, product.getPrice());
        assertEquals(cost, product.getCost());
        assertEquals(2, product.getPrice().scale());
        assertEquals(2, product.getCost().scale());
    }

    @Test
    void increaseAndDecreaseStockChangeOnlyStock() {
        Product product = validProduct("SKU-A1");
        String name = product.getName();
        Category category = product.getCategory();

        product.increaseStock(5);
        product.decreaseStock(3);

        assertEquals(12, product.getStock());
        assertEquals(name, product.getName());
        assertSame(category, product.getCategory());
    }

    @Test
    void stockOperationsRejectNonPositiveAndInsufficientQuantitiesWithoutChangingStock() {
        Product product = validProduct("SKU-A1");

        assertThrows(IllegalArgumentException.class, () -> product.increaseStock(0));
        assertThrows(IllegalArgumentException.class, () -> product.decreaseStock(-1));
        assertThrows(IllegalArgumentException.class, () -> product.decreaseStock(11));

        assertEquals(10, product.getStock());
    }

    @Test
    void increaseStockRejectsOverflowWithoutChangingStock() {
        Product product = productWith("Mouse", "SKU-A1", BigDecimal.ONE, BigDecimal.ONE,
                Integer.MAX_VALUE, 0, validCategory());

        assertThrows(ArithmeticException.class, () -> product.increaseStock(1));

        assertEquals(Integer.MAX_VALUE, product.getStock());
    }

    private static Product validProduct(String sku) {
        return productWith(
                "Mouse",
                sku,
                new BigDecimal("25.50"),
                new BigDecimal("15.25"),
                10,
                2,
                validCategory()
        );
    }

    private static Product productWith(
            String name,
            String sku,
            BigDecimal price,
            BigDecimal cost,
            Integer stock,
            Integer minimumStock,
            Category category
    ) {
        return new Product(
                name,
                sku,
                "Wireless mouse",
                price,
                cost,
                stock,
                minimumStock,
                true,
                category
        );
    }

    private static Category validCategory() {
        return new Category("Electronics", "Electronic products");
    }

    private static boolean hasViolationFor(
            Set<? extends ConstraintViolation<?>> violations,
            String propertyName
    ) {
        return violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(propertyName));
    }
}
