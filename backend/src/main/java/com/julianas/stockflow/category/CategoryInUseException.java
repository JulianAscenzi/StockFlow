package com.julianas.stockflow.category;

public class CategoryInUseException extends RuntimeException {

    public CategoryInUseException(Long id) {
        super("Category is in use and cannot be deleted: " + id);
    }

    public CategoryInUseException(Long id, Throwable cause) {
        super("Category is in use and cannot be deleted: " + id, cause);
    }
}
