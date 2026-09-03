package com.julianas.stockflow.category.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class CategoryRequestValidationTest<T> {

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

    abstract T createRequest(String name, String description);

    @Test
    void validRequestHasNoViolations() {
        assertTrue(validate(createRequest("Electronics", "Electronic products")).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejectsNullEmptyAndWhitespaceOnlyName(String name) {
        assertViolationProperties(createRequest(name, "Description"), "name");
    }

    @Test
    void rejectsNameThatIsTooLong() {
        assertViolationProperties(createRequest("n".repeat(101), null), "name");
    }

    @Test
    void allowsNullDescription() {
        assertTrue(validate(createRequest("Electronics", null)).isEmpty());
    }

    @Test
    void rejectsDescriptionThatIsTooLong() {
        assertViolationProperties(createRequest("Electronics", "d".repeat(256)), "description");
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
