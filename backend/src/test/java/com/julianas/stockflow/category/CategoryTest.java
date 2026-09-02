package com.julianas.stockflow.category;

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

class CategoryTest {

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
    void constructorPreservesNameAndDescription() {
        Category category = new Category("Electronics", "Electronic products");

        assertEquals("Electronics", category.getName());
        assertEquals("Electronic products", category.getDescription());
    }

    @Test
    void updateChangesEditableFields() {
        Category category = new Category("Electronics", "Electronic products");

        category.update("Home appliances", "Products for the home");

        assertEquals("Home appliances", category.getName());
        assertEquals("Products for the home", category.getDescription());
    }

    @Test
    void validationRejectsBlankName() {
        Category category = new Category("   ", "Description");

        Set<ConstraintViolation<Category>> violations = validator.validate(category);

        assertTrue(hasViolationFor(violations, "name"));
    }

    @Test
    void validationRejectsNameAndDescriptionThatAreTooLong() {
        Category category = new Category("n".repeat(101), "d".repeat(256));

        Set<ConstraintViolation<Category>> violations = validator.validate(category);

        assertTrue(hasViolationFor(violations, "name"));
        assertTrue(hasViolationFor(violations, "description"));
    }

    @Test
    void validCategoryHasNoViolations() {
        Category category = new Category("Electronics", "Electronic products");

        Set<ConstraintViolation<Category>> violations = validator.validate(category);

        assertTrue(violations.isEmpty());
    }

    private static boolean hasViolationFor(
            Set<? extends ConstraintViolation<?>> violations,
            String propertyName
    ) {
        return violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(propertyName));
    }
}
