package com.julianas.stockflow.inventory;

public class StockLimitExceededException extends RuntimeException {

    public StockLimitExceededException() {
        super("Stock limit exceeded");
    }
}
