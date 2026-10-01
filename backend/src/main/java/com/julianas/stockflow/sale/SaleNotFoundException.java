package com.julianas.stockflow.sale;

public class SaleNotFoundException extends RuntimeException {
    public SaleNotFoundException(Long id) {
        super("Sale " + id + " was not found.");
    }
}
