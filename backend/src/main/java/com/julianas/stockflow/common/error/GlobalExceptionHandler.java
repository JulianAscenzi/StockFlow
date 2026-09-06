package com.julianas.stockflow.common.error;

import com.julianas.stockflow.category.CategoryInUseException;
import com.julianas.stockflow.category.CategoryNotFoundException;
import com.julianas.stockflow.category.DuplicateCategoryNameException;
import com.julianas.stockflow.inventory.InsufficientStockException;
import com.julianas.stockflow.inventory.StockLimitExceededException;
import com.julianas.stockflow.product.DuplicateProductSkuException;
import com.julianas.stockflow.product.ProductNotFoundException;
import com.julianas.stockflow.sale.EmptySaleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_MESSAGE = "Request validation failed.";

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiError> handleCategoryNotFound(
            CategoryNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                "CATEGORY_NOT_FOUND",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiError> handleProductNotFound(
            ProductNotFoundException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.NOT_FOUND,
                "PRODUCT_NOT_FOUND",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(DuplicateCategoryNameException.class)
    public ResponseEntity<ApiError> handleDuplicateCategoryName(
            DuplicateCategoryNameException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "CATEGORY_NAME_ALREADY_EXISTS",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(DuplicateProductSkuException.class)
    public ResponseEntity<ApiError> handleDuplicateProductSku(
            DuplicateProductSkuException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "PRODUCT_SKU_ALREADY_EXISTS",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<ApiError> handleCategoryInUse(
            CategoryInUseException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "CATEGORY_IN_USE",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ApiError> handleInsufficientStock(
            InsufficientStockException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "INSUFFICIENT_STOCK",
                "Insufficient stock: requested " + exception.getRequestedQuantity()
                        + ", available " + exception.getAvailableStock() + ".",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(StockLimitExceededException.class)
    public ResponseEntity<ApiError> handleStockLimitExceeded(
            StockLimitExceededException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "STOCK_LIMIT_EXCEEDED",
                "The stock cannot be increased further.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(EmptySaleException.class)
    public ResponseEntity<ApiError> handleEmptySale(
            EmptySaleException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "EMPTY_SALE",
                "A sale must contain at least one item.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> addError(
                fieldErrors,
                error.getField(),
                publicValidationMessage(error)
        ));
        exception.getBindingResult().getGlobalErrors().forEach(error -> addError(
                fieldErrors,
                "_global",
                publicValidationMessage(error)
        ));

        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                VALIDATION_MESSAGE,
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> addError(
                fieldErrors,
                lastPathPart(violation),
                publicValidationMessage(violation)
        ));

        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                VALIDATION_MESSAGE,
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "Malformed request body.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "Invalid value for parameter '" + exception.getName() + "'.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                "Invalid request argument.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION",
                "The request conflicts with existing data.",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        LOGGER.error("Unexpected error processing request {}", request.getRequestURI(), exception);

        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, List<String>> fieldErrors
    ) {
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(error);
    }

    private static void addError(
            Map<String, List<String>> fieldErrors,
            String field,
            String message
    ) {
        fieldErrors.computeIfAbsent(field, ignored -> new ArrayList<>()).add(message);
    }

    private static String publicValidationMessage(ObjectError error) {
        return error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage();
    }

    private static String publicValidationMessage(ConstraintViolation<?> violation) {
        return violation.getMessage() == null ? "Invalid value." : violation.getMessage();
    }

    private static String lastPathPart(ConstraintViolation<?> violation) {
        String lastPart = null;
        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getName() != null && !node.getName().isBlank()) {
                lastPart = node.getName();
            }
        }
        return lastPart == null ? "_global" : lastPart;
    }
}
