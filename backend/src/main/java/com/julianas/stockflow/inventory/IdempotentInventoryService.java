package com.julianas.stockflow.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
public class IdempotentInventoryService {
    private final InventoryConfirmationRepository confirmations;
    private final InventoryService inventory;
    private final StockMovementRepository movements;

    public IdempotentInventoryService(InventoryConfirmationRepository confirmations,
            InventoryService inventory, StockMovementRepository movements) {
        this.confirmations = confirmations;
        this.inventory = inventory;
        this.movements = movements;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StockMovement confirm(String keyText, Long productId, StockMovementType type, Integer quantity, String reason) {
        UUID key = UUID.fromString(keyText);
        if (!key.toString().equalsIgnoreCase(keyText)) throw new IllegalArgumentException("Invalid UUID key");
        String hash = fingerprint(productId, type, quantity, reason);
        if (!confirmations.tryLock(key)) throw new InventoryIdempotencyConflictException(true);
        var previous = confirmations.find(key);
        if (previous.isPresent()) {
            if (!previous.get().requestHash().equals(hash)) throw new InventoryIdempotencyConflictException(false);
            return movements.findById(previous.get().movementId()).orElseThrow();
        }
        StockMovement movement = type == StockMovementType.IN
                ? inventory.increaseStock(productId, quantity, reason)
                : inventory.decreaseStock(productId, quantity, reason);
        confirmations.save(key, hash, movement.getId());
        return movement;
    }

    static String fingerprint(Long productId, StockMovementType type, Integer quantity, String reason) {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(quantity, "quantity");
        String normalizedReason = Objects.requireNonNull(reason, "reason").trim();
        if (productId <= 0 || quantity <= 0 || normalizedReason.isEmpty() || normalizedReason.length() > 255) {
            throw new IllegalArgumentException("Invalid movement request");
        }
        // Fixed numeric/enum fields precede the free text, so separators are unambiguous.
        String request = productId + "\n" + type + "\n" + quantity + "\n" + normalizedReason;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(request.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint inventory request", exception);
        }
    }
}
