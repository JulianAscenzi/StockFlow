package com.julianas.stockflow.sale;

public class InactiveProductException extends RuntimeException {
    public InactiveProductException(Long productId) {
        super("Product " + productId + " is inactive.");
    }
}
