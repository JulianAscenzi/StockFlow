package com.julianas.stockflow.sale.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaleCreateRequestTest {

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
    void acceptsValidRequestAndCopiesItems() {
        List<SaleItemRequest> items = new java.util.ArrayList<>(List.of(new SaleItemRequest(1L, 2)));
        SaleCreateRequest request = new SaleCreateRequest("Counter sale", items);
        items.clear();

        assertTrue(validate(request).isEmpty());
        assertEquals(1, request.items().size());
    }

    @Test
    void rejectsMissingOrEmptyItems() {
        assertViolationProperties(new SaleCreateRequest(null, null), "items");
        assertViolationProperties(new SaleCreateRequest(null, List.of()), "items");
    }

    @Test
    void validatesNestedItems() {
        assertViolationProperties(new SaleCreateRequest(null, List.of(new SaleItemRequest(null, 0))),
                "items[0].productId", "items[0].quantity");
    }

    @Test
    void rejectsNotesLongerThan500Characters() {
        assertViolationProperties(new SaleCreateRequest("n".repeat(501), List.of(new SaleItemRequest(1L, 1))), "notes");
    }

    private static Set<ConstraintViolation<SaleCreateRequest>> validate(SaleCreateRequest request) {
        return validator.validate(request);
    }

    private static void assertViolationProperties(SaleCreateRequest request, String... properties) {
        assertEquals(Set.of(properties), validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet()));
    }
}
