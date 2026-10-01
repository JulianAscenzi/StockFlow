package com.julianas.stockflow.sale;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotentSaleServiceTest {
    private final SaleConfirmationRepository repository = mock(SaleConfirmationRepository.class);
    private final SaleService sales = mock(SaleService.class);
    private final IdempotentSaleService service = new IdempotentSaleService(repository, sales);
    private final List<SaleService.SaleLine> lines = List.of(new SaleService.SaleLine(1L, 2));

    @Test
    void fingerprintsNormalizedNotesAndOrderButPreservesContentDifferences() {
        var reversed = List.of(new SaleService.SaleLine(2L, 1), new SaleService.SaleLine(1L, 2));
        var ordered = List.of(new SaleService.SaleLine(1L, 2), new SaleService.SaleLine(2L, 1));
        assertThat(IdempotentSaleService.fingerprint(" note ", reversed))
                .isEqualTo(IdempotentSaleService.fingerprint("note", ordered))
                .isNotEqualTo(IdempotentSaleService.fingerprint("other", ordered));
        assertThat(IdempotentSaleService.fingerprint(null, lines))
                .isEqualTo(IdempotentSaleService.fingerprint("  ", lines))
                .isNotEqualTo(IdempotentSaleService.fingerprint(null, List.of(new SaleService.SaleLine(1L, 1))));
        assertThatThrownBy(() -> IdempotentSaleService.fingerprint(null, List.of(lines.getFirst(), lines.getFirst())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonCanonicalUuidBeforeTakingLock() {
        assertThatThrownBy(() -> service.confirm("1-1-1-1-1", null, lines)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository, sales);
    }

    @Test
    void rejectsConcurrentOperationWithoutReadingOrCreatingSale() {
        UUID key = UUID.randomUUID();
        when(repository.tryLock(key)).thenReturn(false);
        assertThatThrownBy(() -> service.confirm(key.toString(), null, lines))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_IN_PROGRESS");
        verifyNoInteractions(sales);
        verify(repository, never()).find(key);
    }

    @Test
    void replayLoadsHistoricalDetailWithoutConfirmingAgain() {
        UUID key = UUID.randomUUID();
        when(repository.tryLock(key)).thenReturn(true);
        when(repository.find(key)).thenReturn(Optional.of(new SaleConfirmationRepository.Confirmation(
                IdempotentSaleService.fingerprint(null, lines), 42L)));
        Sale original = mock(Sale.class);
        when(sales.detail(42L)).thenReturn(original);
        assertThat(service.confirm(key.toString(), null, lines)).isSameAs(original);
        verify(sales, never()).confirm(any(), anyList());
        assertThatThrownBy(() -> service.confirm(key.toString(), "changed", lines))
                .isInstanceOf(IdempotencyConflictException.class)
                .extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }
}
