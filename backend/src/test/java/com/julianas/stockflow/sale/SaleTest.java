package com.julianas.stockflow.sale;

import com.julianas.stockflow.product.Product;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaleTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void newSaleStartsAtZeroWithNoItemsAndNormalizesNotes() {
        Sale sale = new Sale("  Counter sale  ");

        assertEquals(new BigDecimal("0.00"), sale.getTotal());
        assertTrue(sale.getItems().isEmpty());
        assertEquals("Counter sale", sale.getNotes());
    }

    @Test
    void convertsBlankNotesToNullAndRejectsLongNotes() {
        assertNull(new Sale("   ").getNotes());
        assertThrows(IllegalArgumentException.class, () -> new Sale("x".repeat(501)));
    }

    @Test
    void addItemCreatesHistoricalSnapshotAndUpdatesTotal() {
        Sale sale = new Sale(null);
        Product product = product(1L, "Mouse", "MOU-01", "12.50", "7.25", 9);

        SaleItem item = sale.addItem(product, 3);

        assertEquals(1, sale.getItems().size());
        assertEquals(sale, item.getSale());
        assertEquals(product, item.getProduct());
        assertEquals("Mouse", item.getProductName());
        assertEquals("MOU-01", item.getProductSku());
        assertEquals(new BigDecimal("12.50"), item.getUnitPrice());
        assertEquals(new BigDecimal("7.25"), item.getUnitCost());
        assertEquals(new BigDecimal("37.50"), item.getSubtotal());
        assertEquals(new BigDecimal("37.50"), sale.getTotal());
        verify(product, never()).decreaseStock(anyInt());
    }

    @Test
    void multipleItemsAccumulateTotal() {
        Sale sale = new Sale(null);

        sale.addItem(product(1L, "Mouse", "MOU-01", "12.50", "7.25", 9), 2);
        sale.addItem(product(2L, "Keyboard", "KEY-01", "20.00", "10.00", 4), 1);

        assertEquals(new BigDecimal("45.00"), sale.getTotal());
        assertEquals(2, sale.getItems().size());
        assertEquals("MOU-01", sale.getItems().getFirst().getProductSku());
        assertEquals("KEY-01", sale.getItems().get(1).getProductSku());
    }

    @Test
    void rejectsNullOrTransientProductAndInvalidQuantityWithoutMutation() {
        Sale sale = new Sale(null);
        Product persisted = product(1L, "Mouse", "MOU-01", "1.00", "0.50", 9);

        assertThrows(NullPointerException.class, () -> sale.addItem(null, 1));
        assertThrows(NullPointerException.class, () -> sale.addItem(product(null, "Mouse", "MOU-01", "1.00", "0.50", 9), 1));
        assertThrows(IllegalArgumentException.class, () -> sale.addItem(persisted, 0));
        assertThrows(IllegalArgumentException.class, () -> sale.addItem(persisted, -1));
        assertTrue(sale.getItems().isEmpty());
        assertEquals(new BigDecimal("0.00"), sale.getTotal());
    }

    @Test
    void rejectsDuplicateProductIdWithoutMutation() {
        Sale sale = new Sale(null);
        sale.addItem(product(1L, "Mouse", "MOU-01", "1.00", "0.50", 9), 1);

        assertThrows(IllegalArgumentException.class,
                () -> sale.addItem(product(1L, "Renamed mouse", "MOU-02", "2.00", "1.00", 9), 1));

        assertEquals(1, sale.getItems().size());
        assertEquals(new BigDecimal("1.00"), sale.getTotal());
    }

    @Test
    void rejectsMonetaryValuesWithMoreThanTwoDecimalsWithoutRounding() {
        Sale sale = new Sale(null);

        assertThrows(IllegalArgumentException.class,
                () -> sale.addItem(product(1L, "Mouse", "MOU-01", "1.001", "0.50", 9), 1));
        assertThrows(IllegalArgumentException.class,
                () -> sale.addItem(product(2L, "Keyboard", "KEY-01", "1.00", "0.501", 9), 1));
        assertTrue(sale.getItems().isEmpty());
        assertEquals(new BigDecimal("0.00"), sale.getTotal());
    }

    @Test
    void rejectsSubtotalAndAccumulatedTotalOutsideNumeric14_2() {
        Sale sale = new Sale(null);
        assertThrows(IllegalArgumentException.class,
                () -> sale.addItem(product(1L, "Mouse", "MOU-01", "9999999999.99", "0.00", 9), 101));

        sale.addItem(product(2L, "Keyboard", "KEY-01", "9999999999.99", "0.00", 9), 100);
        assertThrows(IllegalArgumentException.class,
                () -> sale.addItem(product(3L, "Cable", "CAB-01", "1.00", "0.00", 9), 1));

        assertEquals(1, sale.getItems().size());
        assertEquals(new BigDecimal("999999999999.00"), sale.getTotal());
    }

    @Test
    void itemsCannotBeModifiedExternallyAndValidAggregatePassesBeanValidation() {
        Sale sale = new Sale(null);
        SaleItem item = sale.addItem(product(1L, "Mouse", "MOU-01", "1.00", "0.50", 9), 1);

        assertThrows(UnsupportedOperationException.class, () -> sale.getItems().add(item));
        assertFalse(validator.validate(sale).iterator().hasNext());
        assertFalse(validator.validate(item).iterator().hasNext());
    }

    private Product product(Long id, String name, String sku, String price, String cost, int stock) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn(name);
        when(product.getSku()).thenReturn(sku);
        when(product.getPrice()).thenReturn(new BigDecimal(price));
        when(product.getCost()).thenReturn(new BigDecimal(cost));
        when(product.getStock()).thenReturn(stock);
        return product;
    }
}
