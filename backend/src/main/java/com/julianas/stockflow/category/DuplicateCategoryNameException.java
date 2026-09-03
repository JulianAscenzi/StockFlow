package com.julianas.stockflow.category;

public class DuplicateCategoryNameException extends RuntimeException {

    public DuplicateCategoryNameException(String name) {
        super("A category already exists with name: " + name);
    }
}
