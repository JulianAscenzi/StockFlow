package com.julianas.stockflow.sale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class IdempotentSaleService {
    private final SaleConfirmationRepository confirmations;
    private final SaleService sales;

    public IdempotentSaleService(SaleConfirmationRepository confirmations, SaleService sales) {
        this.confirmations = confirmations;
        this.sales = sales;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Sale confirm(String keyText, String notes, List<SaleService.SaleLine> lines) {
        UUID key = UUID.fromString(keyText);
        if (!key.toString().equalsIgnoreCase(keyText)) throw new IllegalArgumentException("Invalid UUID key");
        String hash = fingerprint(notes, lines);
        if (!confirmations.tryLock(key)) throw new IdempotencyConflictException(true);
        var previous = confirmations.find(key);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(hash)) throw new IdempotencyConflictException(false);
            return sales.detail(previous.get().saleId());
        }
        Sale sale = sales.confirm(notes, lines);
        confirmations.save(key, hash, sale.getId());
        return sale;
    }

    static String fingerprint(String notes, List<SaleService.SaleLine> lines) {
        String normalizedNotes = Sale.normalizeNotes(notes);
        List<SaleService.SaleLine> ordered = lines.stream()
                .sorted(Comparator.comparing(SaleService.SaleLine::productId)).toList();
        HashSet<Long> products = new HashSet<>();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                byte[] text = (normalizedNotes == null ? "" : normalizedNotes).getBytes(StandardCharsets.UTF_8);
                data.writeInt(text.length);
                data.write(text);
                data.writeInt(ordered.size());
                for (var line : ordered) {
                    if (!products.add(line.productId())) throw new IllegalArgumentException("Duplicate product");
                    data.writeLong(line.productId());
                    data.writeInt(line.quantity());
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint sale", exception);
        }
    }
}
