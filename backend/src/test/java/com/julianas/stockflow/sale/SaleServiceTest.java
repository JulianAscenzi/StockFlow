package com.julianas.stockflow.sale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;

    private SaleService saleService;

    @BeforeEach
    void setUp() {
        saleService = new SaleService(saleRepository);
    }

    @Test
    void confirmsSaleWithItemsAndConsistentTotal() {
        Sale sale = saleWithTotal("25.00", "10.00", "15.00");
        when(saleRepository.save(sale)).thenReturn(sale);

        Sale confirmed = saleService.confirm(sale);

        assertSame(sale, confirmed);
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
}
