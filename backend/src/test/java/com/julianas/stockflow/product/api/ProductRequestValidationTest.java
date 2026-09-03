package com.julianas.stockflow.product.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class ProductRequestValidationTest<T> {

    private static final String VALID_NAME = "Wireless mouse";
    private static final String VALID_SKU = "MOU-01";
    private static final String VALID_DESCRIPTION = "Ergonomic wireless mouse";
    private static final BigDecimal VALID_PRICE = new BigDecimal("25.50");
    private static final BigDecimal VALID_COST = new BigDecimal("15.25");
    private static final Integer VALID_MINIMUM_STOCK = 2;
    private static final Long VALID_CATEGORY_ID = 10L;

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

    abstract T createRequest(
            String name,
            String sku,
            String description,
            BigDecimal price,
            BigDecimal cost,
            Integer minimumStock,
            Long categoryId
    );

    @Test
    void validRequestHasNoViolations() {
        assertTrue(validate(validRequest()).isEmpty());
    }

    @Test
    void rejectsEmptyNameAndSku() {
        assertViolationProperties(requestWith("", "", VALID_DESCRIPTION, VALID_PRICE, VALID_COST,
                VALID_MINIMUM_STOCK, VALID_CATEGORY_ID), "name", "sku");
    }

    @Test
    void rejectsNameAndSkuThatAreTooLong() {
        assertViolationProperties(requestWith("n".repeat(151), "s".repeat(51), VALID_DESCRIPTION,
                VALID_PRICE, VALID_COST, VALID_MINIMUM_STOCK, VALID_CATEGORY_ID), "name", "sku");
    }

    @Test
    void allowsNullDescription() {
        assertTrue(validate(requestWith(VALID_NAME, VALID_SKU, null, VALID_PRICE, VALID_COST,
                VALID_MINIMUM_STOCK, VALID_CATEGORY_ID)).isEmpty());
    }

    @Test
    void rejectsDescriptionThatIsTooLong() {
        assertViolationProperties(requestWith(VALID_NAME, VALID_SKU, "d".repeat(501), VALID_PRICE,
                VALID_COST, VALID_MINIMUM_STOCK, VALID_CATEGORY_ID), "description");
    }

    @Test
    void rejectsNullPrice() {
        assertViolationProperties(requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION, null,
                VALID_COST, VALID_MINIMUM_STOCK, VALID_CATEGORY_ID), "price");
    }

    @Test
    void rejectsNegativePrice() {
        assertViolationProperties(requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION,
                new BigDecimal("-0.01"), VALID_COST, VALID_MINIMUM_STOCK, VALID_CATEGORY_ID), "price");
    }

    @Test
    void rejectsNullCost() {
        assertViolationProperties(requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION, VALID_PRICE,
                null, VALID_MINIMUM_STOCK, VALID_CATEGORY_ID), "cost");
    }

    @Test
    void rejectsNegativeCost() {
        assertViolationProperties(requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION, VALID_PRICE,
                new BigDecimal("-0.01"), VALID_MINIMUM_STOCK, VALID_CATEGORY_ID), "cost");
    }

    @Test
    void rejectsNullMinimumStock() {
        assertViolationProperties(requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION, VALID_PRICE,
                VALID_COST, null, VALID_CATEGORY_ID), "minimumStock");
    }

    @Test
    void rejectsNegativeMinimumStock() {
        assertViolationProperties(requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION, VALID_PRICE,
                VALID_COST, -1, VALID_CATEGORY_ID), "minimumStock");
    }

    @Test
    void rejectsNullCategoryId() {
        assertViolationProperties(requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION, VALID_PRICE,
                VALID_COST, VALID_MINIMUM_STOCK, null), "categoryId");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveCategoryId(long categoryId) {
        assertViolationProperties(requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION, VALID_PRICE,
                VALID_COST, VALID_MINIMUM_STOCK, categoryId), "categoryId");
    }

    @Test
    void acceptsBigDecimalValuesWithDecimals() {
        T request = requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION, new BigDecimal("123.45"),
                new BigDecimal("67.89"), VALID_MINIMUM_STOCK, VALID_CATEGORY_ID);

        assertTrue(validate(request).isEmpty());
    }

    private T validRequest() {
        return requestWith(VALID_NAME, VALID_SKU, VALID_DESCRIPTION, VALID_PRICE, VALID_COST,
                VALID_MINIMUM_STOCK, VALID_CATEGORY_ID);
    }

    private T requestWith(
            String name,
            String sku,
            String description,
            BigDecimal price,
            BigDecimal cost,
            Integer minimumStock,
            Long categoryId
    ) {
        return createRequest(name, sku, description, price, cost, minimumStock, categoryId);
    }

    private static Set<ConstraintViolation<Object>> validate(Object request) {
        return validator.validate(request);
    }

    private static void assertViolationProperties(Object request, String... expectedProperties) {
        Set<String> properties = validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of(expectedProperties), properties);
    }
}
