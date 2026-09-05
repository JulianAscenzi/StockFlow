package com.julianas.stockflow.inventory;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductNotFoundException;
import com.julianas.stockflow.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(productRepository, stockMovementRepository);
    }

    @Test
    void increaseLocksProductUpdatesStockAndSavesMovement() {
        Product product = product(10, true);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(stockMovementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StockMovement result = inventoryService.increaseStock(1L, 5, "  Delivery  ");

        assertEquals(15, product.getStock());
        assertEquals(StockMovementType.IN, result.getMovementType());
        assertEquals(10, result.getStockBefore());
        assertEquals(15, result.getStockAfter());
        assertEquals("Delivery", result.getReason());
        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository, never()).save(any());
    }

    @Test
    void decreaseLocksProductUpdatesStockAndSavesMovement() {
        Product product = product(10, true);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(stockMovementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StockMovement result = inventoryService.decreaseStock(1L, 4, "Sale");

        assertEquals(6, product.getStock());
        assertEquals(StockMovementType.OUT, result.getMovementType());
        assertEquals(10, result.getStockBefore());
        assertEquals(6, result.getStockAfter());
        verify(productRepository).findByIdForUpdate(1L);
        verify(productRepository, never()).save(any());
    }

    @Test
    void missingProductThrows() {
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> inventoryService.increaseStock(99L, 1, "Count"));
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void invalidArgumentsAreRejectedBeforeLocking() {
        assertThrows(NullPointerException.class, () -> inventoryService.increaseStock(null, 1, "Count"));
        assertThrows(NullPointerException.class, () -> inventoryService.increaseStock(1L, null, "Count"));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.increaseStock(1L, 0, "Count"));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.increaseStock(1L, -1, "Count"));
        assertThrows(NullPointerException.class, () -> inventoryService.increaseStock(1L, 1, null));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.increaseStock(1L, 1, "  "));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.increaseStock(1L, 1, "r".repeat(256)));

        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void insufficientStockDoesNotChangeProductOrSaveMovement() {
        Product product = product(3, true);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class, () -> inventoryService.decreaseStock(1L, 4, "Sale")
        );

        assertEquals(1L, exception.getProductId());
        assertEquals(3, exception.getAvailableStock());
        assertEquals(4, exception.getRequestedQuantity());
        assertEquals(3, product.getStock());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void overflowDoesNotChangeProductOrSaveMovement() {
        Product product = product(Integer.MAX_VALUE, false);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThrows(StockLimitExceededException.class, () -> inventoryService.increaseStock(1L, 1, "Count"));

        assertEquals(Integer.MAX_VALUE, product.getStock());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void inactiveProductCanReceiveMovement() {
        Product product = product(0, false);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(stockMovementRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.increaseStock(1L, 1, "Correction");

        assertEquals(1, product.getStock());
    }

    @Test
    void historyChecksExistenceAndDelegatesPageable() {
        PageRequest pageable = PageRequest.of(1, 2);
        Page<StockMovement> expected = new PageImpl<>(List.of());
        when(productRepository.existsById(1L)).thenReturn(true);
        when(stockMovementRepository.findByProductIdOrderByCreatedAtDescIdDesc(1L, pageable)).thenReturn(expected);

        assertSame(expected, inventoryService.getHistory(1L, pageable));
        verify(stockMovementRepository).findByProductIdOrderByCreatedAtDescIdDesc(1L, pageable);
    }

    @Test
    void historyRejectsMissingProduct() {
        when(productRepository.existsById(1L)).thenReturn(false);

        assertThrows(ProductNotFoundException.class, () -> inventoryService.getHistory(1L, PageRequest.of(0, 10)));
        verify(stockMovementRepository, never()).findByProductIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    private Product product(int stock, boolean active) {
        return new Product("Mouse", "MOU-01", null, BigDecimal.ONE, BigDecimal.ONE, stock, 0, active,
                new Category("Peripherals", null));
    }
}
