package com.julianas.stockflow.sale;

import com.julianas.stockflow.inventory.InventoryService;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductRepository;
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

    @Mock
    private ProductRepository productRepository;

    private SaleService saleService;

    @BeforeEach
    void setUp() {
        saleService = new SaleService(saleRepository, inventoryService, productRepository);
    }

    @Test
    void requestLocksProductsInIdOrderBeforeCreatingSnapshotsAndWithdrawingStock() {
        for (long id : List.of(1L, 2L)) {
            Product product = mock(Product.class);
            when(product.getId()).thenReturn(id);
            when(product.getName()).thenReturn("Product " + id);
            when(product.getSku()).thenReturn("SKU-" + id);
            when(product.getPrice()).thenReturn(new BigDecimal("10.00"));
            when(product.getCost()).thenReturn(new BigDecimal("4.00"));
            when(productRepository.findByIdForUpdate(id)).thenReturn(java.util.Optional.of(product));
        }

        saleService.confirm(null, List.of(new SaleService.SaleLine(2L, 1), new SaleService.SaleLine(1L, 1)));

        InOrder order = inOrder(productRepository, inventoryService);
        order.verify(productRepository).findByIdForUpdate(1L);
        order.verify(productRepository).findByIdForUpdate(2L);
        order.verify(inventoryService).decreaseStock(1L, 1, "Sale");
        order.verify(inventoryService).decreaseStock(2L, 1, "Sale");
        verify(productRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
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

    @Test
    void historyCapsPageSizeAndDetailRejectsMissingSale() {
        saleService.history(2, 200);
        verify(saleRepository).findAllByOrderByCreatedAtDescIdDesc(org.springframework.data.domain.PageRequest.of(2, 100));
        assertThrows(SaleNotFoundException.class, () -> saleService.detail(999L));
        org.mockito.Mockito.verifyNoInteractions(inventoryService, productRepository);
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
