package com.julianas.stockflow.sale;

import com.julianas.stockflow.inventory.InventoryService;
import com.julianas.stockflow.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private InventoryService inventoryService;

    private SaleService saleService;

    @BeforeEach
    void setUp() {
        saleService = new SaleService(saleRepository, inventoryService);
    }

    @Test
    void confirmsSaleWithItemsAndConsistentTotal() {
        Sale sale = saleWithTotal("25.00", "10.00", "15.00");
        long productId = 1;
        for (SaleItem item : sale.getItems()) {
            Product product = mock(Product.class);
            when(product.getId()).thenReturn(productId++);
            when(item.getProduct()).thenReturn(product);
            when(item.getQuantity()).thenReturn(1);
        }
        when(saleRepository.save(sale)).thenReturn(sale);

        Sale confirmed = saleService.confirm(sale);

        assertSame(sale, confirmed);
        verify(inventoryService).decreaseStock(1L, 1, "Sale");
        verify(inventoryService).decreaseStock(2L, 1, "Sale");
        verify(saleRepository).save(sale);
    }

    @Test
    void rejectsEmptySaleWithoutPersisting() {
        Sale sale = new Sale(null);

        assertThrows(EmptySaleException.class, () -> saleService.confirm(sale));

        verify(saleRepository, never()).save(sale);
    }

    @Test
    void rejectsInconsistentTotalWithoutPersisting() {
        Sale sale = saleWithTotal("24.99", "10.00", "15.00");

        assertThrows(IllegalArgumentException.class, () -> saleService.confirm(sale));

        verify(saleRepository, never()).save(sale);
    }

    @Test
    void decreasesStockInAscendingProductIdOrder() {
        Product firstProduct = mock(Product.class);
        when(firstProduct.getId()).thenReturn(1L);
        Product secondProduct = mock(Product.class);
        when(secondProduct.getId()).thenReturn(2L);
        SaleItem firstItem = saleItem(firstProduct, 1, "10.00");
        SaleItem secondItem = saleItem(secondProduct, 1, "15.00");
        Sale sale = mock(Sale.class);
        when(sale.getItems()).thenReturn(List.of(secondItem, firstItem));
        when(sale.getTotal()).thenReturn(new BigDecimal("25.00"));
        when(saleRepository.save(sale)).thenReturn(sale);

        saleService.confirm(sale);

        InOrder order = inOrder(inventoryService);
        order.verify(inventoryService).decreaseStock(1L, 1, "Sale");
        order.verify(inventoryService).decreaseStock(2L, 1, "Sale");
    }

    private Sale saleWithTotal(String total, String... subtotals) {
        Sale sale = mock(Sale.class);
        List<SaleItem> items = java.util.Arrays.stream(subtotals)
                .map(subtotal -> {
                    SaleItem item = mock(SaleItem.class);
                    when(item.getSubtotal()).thenReturn(new BigDecimal(subtotal));
                    return item;
                })
                .toList();
        when(sale.getItems()).thenReturn(items);
        when(sale.getTotal()).thenReturn(new BigDecimal(total));
        return sale;
    }

    private SaleItem saleItem(Product product, int quantity, String subtotal) {
        SaleItem item = mock(SaleItem.class);
        when(item.getProduct()).thenReturn(product);
        when(item.getQuantity()).thenReturn(quantity);
        when(item.getSubtotal()).thenReturn(new BigDecimal(subtotal));
        return item;
    }
}
