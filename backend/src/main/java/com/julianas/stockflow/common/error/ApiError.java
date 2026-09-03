package com.julianas.stockflow.common.error;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, List<String>> fieldErrors
) {

    public ApiError {
        fieldErrors = immutableFieldErrors(fieldErrors);
    }

    private static Map<String, List<String>> immutableFieldErrors(
            Map<String, List<String>> fieldErrors
    ) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> copy = new LinkedHashMap<>();
        fieldErrors.forEach((field, messages) -> copy.put(field, List.copyOf(messages)));
        return Collections.unmodifiableMap(copy);
    }
}
