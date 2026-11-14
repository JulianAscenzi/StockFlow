package com.julianas.stockflow.inventory;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotentInventoryServiceTest {
    private final InventoryConfirmationRepository confirmations = mock(InventoryConfirmationRepository.class);
    private final InventoryService inventory = mock(InventoryService.class);
    private final StockMovementRepository movements = mock(StockMovementRepository.class);
    private final IdempotentInventoryService service = new IdempotentInventoryService(confirmations, inventory, movements);

    @Test
    void fingerprintIncludesProductDirectionQuantityAndNormalizedReason() {
        String hash = IdempotentInventoryService.fingerprint(1L, StockMovementType.IN, 2, " Delivery ");
        assertThat(hash).isEqualTo(IdempotentInventoryService.fingerprint(1L, StockMovementType.IN, 2, "Delivery"))
                .isNotEqualTo(IdempotentInventoryService.fingerprint(2L, StockMovementType.IN, 2, "Delivery"))
                .isNotEqualTo(IdempotentInventoryService.fingerprint(1L, StockMovementType.OUT, 2, "Delivery"))
                .isNotEqualTo(IdempotentInventoryService.fingerprint(1L, StockMovementType.IN, 3, "Delivery"))
                .isNotEqualTo(IdempotentInventoryService.fingerprint(1L, StockMovementType.IN, 2, "Other"));
    }

    @Test
    void rejectsInvalidKeyAndRequestBeforeAccessingDatabase() {
        assertThatThrownBy(() -> service.confirm("1-1-1-1-1", 1L, StockMovementType.IN, 2, "Delivery"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.confirm(UUID.randomUUID().toString(), 1L, StockMovementType.IN, 0, "Delivery"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(confirmations, inventory, movements);
    }

    @Test
    void rejectsInProgressWithoutApplyingMovement() {
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> service.confirm(key.toString(), 1L, StockMovementType.IN, 2, "Delivery"))
                .isInstanceOf(InventoryIdempotencyConflictException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_IN_PROGRESS");
        verify(confirmations, never()).find(key);
        verifyNoInteractions(inventory, movements);
    }

    @Test
    void replayLoadsHistoricalMovementAndDifferentRequestConflicts() {
        UUID key = UUID.randomUUID();
        when(confirmations.tryLock(key)).thenReturn(true);
        when(confirmations.find(key)).thenReturn(Optional.of(new InventoryConfirmationRepository.Confirmation(
                IdempotentInventoryService.fingerprint(1L, StockMovementType.OUT, 2, "Loss"), 42L)));
        StockMovement original = mock(StockMovement.class);
        when(movements.findById(42L)).thenReturn(Optional.of(original));
        assertThat(service.confirm(key.toString(), 1L, StockMovementType.OUT, 2, " Loss ")).isSameAs(original);
        assertThatThrownBy(() -> service.confirm(key.toString(), 1L, StockMovementType.OUT, 3, "Loss"))
                .isInstanceOf(InventoryIdempotencyConflictException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verifyNoInteractions(inventory);
        verify(confirmations, never()).save(any(), any(), any());
    }

    @Test
    void newMovementStoresItsResultAfterApplyingInventory() {
        UUID key = UUID.randomUUID();
        when(confirmations.tryLock(key)).thenReturn(true);
        when(confirmations.find(key)).thenReturn(Optional.empty());
        StockMovement movement = mock(StockMovement.class);
        when(movement.getId()).thenReturn(7L);
        when(inventory.increaseStock(1L, 2, "Delivery")).thenReturn(movement);
        assertThat(service.confirm(key.toString(), 1L, StockMovementType.IN, 2, "Delivery")).isSameAs(movement);
        var order = inOrder(confirmations, inventory);
        order.verify(inventory).increaseStock(1L, 2, "Delivery");
        order.verify(confirmations).save(key, IdempotentInventoryService.fingerprint(1L, StockMovementType.IN, 2, "Delivery"), 7L);
    }
}
