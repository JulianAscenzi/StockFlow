package com.julianas.stockflow.dashboard;

public record LowStockProduct(Long id, String name, String sku, Integer stock, Integer minimumStock) {
}
