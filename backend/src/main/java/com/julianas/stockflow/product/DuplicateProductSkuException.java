package com.julianas.stockflow.product;

public class DuplicateProductSkuException extends RuntimeException {

    public DuplicateProductSkuException(String sku) {
        super("A product already exists with SKU: " + sku);
    }
}
