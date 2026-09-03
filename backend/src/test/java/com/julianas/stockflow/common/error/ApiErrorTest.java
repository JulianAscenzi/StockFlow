package com.julianas.stockflow.common.error;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiErrorTest {

    @Test
    void convertsNullFieldErrorsToEmptyMap() {
        ApiError error = apiError(null);

        assertTrue(error.fieldErrors().isEmpty());
    }

    @Test
    void makesDeepImmutableCopyOfFieldErrors() {
        List<String> messages = new ArrayList<>(List.of("must not be blank"));
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put("name", messages);

        ApiError error = apiError(fieldErrors);
        messages.add("must be uppercase");
        fieldErrors.put("sku", new ArrayList<>(List.of("must not be blank")));

        assertEquals(Map.of("name", List.of("must not be blank")), error.fieldErrors());
        assertThrows(
                UnsupportedOperationException.class,
                () -> error.fieldErrors().put("sku", List.of("invalid"))
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> error.fieldErrors().get("name").add("invalid")
        );
    }

    private ApiError apiError(Map<String, List<String>> fieldErrors) {
        return new ApiError(
                Instant.parse("2026-01-10T12:00:00Z"),
                400,
                "Bad Request",
                "VALIDATION_ERROR",
                "Request validation failed.",
                "/test",
                fieldErrors
        );
    }
}
