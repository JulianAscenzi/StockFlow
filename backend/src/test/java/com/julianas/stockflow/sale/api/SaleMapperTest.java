package com.julianas.stockflow.sale.api;

import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.sale.Sale;
import com.julianas.stockflow.sale.SaleItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SaleMapperTest {

    private final SaleMapper mapper = new SaleMapper();

    @Test
    void mapsSaleAndHistoricalItemSnapshots() {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(8L);
        SaleItem item = mock(SaleItem.class);
        when(item.getId()).thenReturn(21L);
        when(item.getProduct()).thenReturn(product);
        when(item.getProductName()).thenReturn("Mouse");
        when(item.getProductSku()).thenReturn("MOU-01");
        when(item.getQuantity()).thenReturn(2);
        when(item.getUnitPrice()).thenReturn(new BigDecimal("12.50"));
        when(item.getUnitCost()).thenReturn(new BigDecimal("7.25"));
        when(item.getSubtotal()).thenReturn(new BigDecimal("25.00"));
        Sale sale = mock(Sale.class);
        Instant createdAt = Instant.parse("2026-03-01T10:15:30Z");
        when(sale.getId()).thenReturn(15L);
        when(sale.getTotal()).thenReturn(new BigDecimal("25.00"));
        when(sale.getNotes()).thenReturn("Counter sale");
        when(sale.getCreatedAt()).thenReturn(createdAt);
        when(sale.getItems()).thenReturn(List.of(item));

        SaleResponse response = mapper.toResponse(sale);

        assertEquals(15L, response.id());
        assertEquals(new BigDecimal("25.00"), response.total());
        assertEquals("Counter sale", response.notes());
        assertEquals(createdAt, response.createdAt());
        SaleItemResponse itemResponse = response.items().getFirst();
        assertEquals(21L, itemResponse.id());
        assertEquals(8L, itemResponse.productId());
        assertEquals("Mouse", itemResponse.productName());
        assertEquals("MOU-01", itemResponse.productSku());
        assertEquals(2, itemResponse.quantity());
        assertEquals(new BigDecimal("12.50"), itemResponse.unitPrice());
        assertEquals(new BigDecimal("7.25"), itemResponse.unitCost());
        assertEquals(new BigDecimal("25.00"), itemResponse.subtotal());
    }

    @Test
    void rejectsNullSale() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> mapper.toResponse(null));

        assertEquals("sale must not be null", exception.getMessage());
    }
}
