package com.julianas.stockflow.sale;

public class EmptySaleException extends RuntimeException {

    public EmptySaleException() {
        super("a sale must contain at least one item");
    }
}
