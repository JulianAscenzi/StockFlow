package com.julianas.stockflow.common.error;

import com.jayway.jsonpath.JsonPath;
import com.julianas.stockflow.category.CategoryInUseException;
import com.julianas.stockflow.category.CategoryNotFoundException;
import com.julianas.stockflow.category.DuplicateCategoryNameException;
import com.julianas.stockflow.category.api.CategoryCreateRequest;
import com.julianas.stockflow.product.DuplicateProductSkuException;
import com.julianas.stockflow.product.ProductNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController(validator))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void handlesCategoryNotFoundWithPathTimestampAndEmptyFieldErrors() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/categories/91"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/test/categories/91"))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andReturn();

        String timestamp = JsonPath.read(result.getResponse().getContentAsString(), "$.timestamp");
        assertDoesNotThrow(() -> Instant.parse(timestamp));
    }

    @Test
    void handlesProductNotFound() throws Exception {
        mockMvc.perform(get("/test/products/92"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void handlesDuplicateCategoryName() throws Exception {
        mockMvc.perform(get("/test/duplicate-category"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_ALREADY_EXISTS"));
    }

    @Test
    void handlesDuplicateProductSku() throws Exception {
        mockMvc.perform(get("/test/duplicate-product"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_SKU_ALREADY_EXISTS"));
    }

    @Test
    void handlesCategoryInUse() throws Exception {
        mockMvc.perform(get("/test/category-in-use"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
    }

    @Test
    void handlesInvalidRequestDtoWithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").isArray())
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
    }

    @Test
    void groupsMultipleMessagesForSameField() throws Exception {
        mockMvc.perform(post("/test/multiple-validation-errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code", hasSize(2)));
    }

    @Test
    void preservesGlobalObjectErrors() throws Exception {
        mockMvc.perform(post("/test/global-validation-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"first\":\"one\",\"second\":\"two\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors._global[0]").value("Values are incompatible."));
    }

    @Test
    void handlesConstraintViolationUsingLastPropertyPathPart() throws Exception {
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.query").isNotEmpty());
    }

    @Test
    void handlesMalformedJsonWithoutInternalDetails() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body."))
                .andExpect(content().string(not(containsString("Json"))))
                .andExpect(content().string(not(containsString("Jackson"))));
    }

    @Test
    void handlesIncorrectParameterTypeWithoutJavaClassNames() throws Exception {
        mockMvc.perform(get("/test/parameters/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'."))
                .andExpect(content().string(not(containsString("java.lang"))));
    }

    @Test
    void handlesIllegalArgumentWithGenericMessage() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.message").value("Invalid request argument."))
                .andExpect(content().string(not(containsString("internal implementation detail"))));
    }

    @Test
    void handlesDataIntegrityViolationWithoutDatabaseDetails() throws Exception {
        mockMvc.perform(get("/test/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value("The request conflicts with existing data."))
                .andExpect(content().string(not(containsString("SQL"))))
                .andExpect(content().string(not(containsString("uq_products_sku"))))
                .andExpect(content().string(not(containsString("PostgreSQL"))));
    }

    @Test
    void handlesUnexpectedExceptionWithoutTechnicalDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("sensitive server detail"))));
    }

    @RestController
    @RequestMapping("/test")
    private static class TestController {

        private final jakarta.validation.Validator validator;

        private TestController(jakarta.validation.Validator validator) {
            this.validator = validator;
        }

        @InitBinder("globalValidationRequest")
        void configureValidation(WebDataBinder binder) {
            binder.addValidators(new GlobalRequestValidator());
        }

        @GetMapping("/categories/{id}")
        void categoryNotFound(@PathVariable Long id) {
            throw new CategoryNotFoundException(id);
        }

        @GetMapping("/products/{id}")
        void productNotFound(@PathVariable Long id) {
            throw new ProductNotFoundException(id);
        }

        @GetMapping("/duplicate-category")
        void duplicateCategory() {
            throw new DuplicateCategoryNameException("Electronics");
        }

        @GetMapping("/duplicate-product")
        void duplicateProduct() {
            throw new DuplicateProductSkuException("MOU-01");
        }

        @GetMapping("/category-in-use")
        void categoryInUse() {
            throw new CategoryInUseException(7L);
        }

        @PostMapping("/validation")
        void validateRequest(@Valid @RequestBody CategoryCreateRequest request) {
        }

        @PostMapping("/multiple-validation-errors")
        void validateMultipleErrors(@Valid @RequestBody MultipleErrorsRequest request) {
        }

        @PostMapping("/global-validation-error")
        void validateGlobalError(@Valid @RequestBody GlobalValidationRequest request) {
        }

        @GetMapping("/constraint-violation")
        void constraintViolation() {
            Set<ConstraintViolation<ConstraintRequest>> violations = validator.validate(
                    new ConstraintRequest("")
            );
            throw new ConstraintViolationException(violations);
        }

        @GetMapping("/parameters/{id}")
        void parameter(@PathVariable Long id) {
        }

        @GetMapping("/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("internal implementation detail");
        }

        @GetMapping("/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException(
                    "SQL failed for constraint uq_products_sku in PostgreSQL"
            );
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("sensitive server detail");
        }
    }

    private record MultipleErrorsRequest(
            @NotBlank @Pattern(regexp = "[A-Z]+") String code
    ) {
    }

    private record GlobalValidationRequest(String first, String second) {
    }

    private record ConstraintRequest(@NotBlank String query) {
    }

    private static class GlobalRequestValidator implements Validator {

        @Override
        public boolean supports(Class<?> type) {
            return GlobalValidationRequest.class.equals(type);
        }

        @Override
        public void validate(Object target, Errors errors) {
            errors.reject("incompatible", "Values are incompatible.");
        }
    }
}
