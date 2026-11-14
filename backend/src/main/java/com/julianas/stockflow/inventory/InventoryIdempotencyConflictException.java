package com.julianas.stockflow.inventory;

public class InventoryIdempotencyConflictException extends RuntimeException {
    private final boolean inProgress;

    public InventoryIdempotencyConflictException(boolean inProgress) {
        super(inProgress ? "A movement with this key is still in progress. Retry with the same key."
                : "This key was already used for a different inventory request.");
        this.inProgress = inProgress;
    }

    public String getCode() {
        return inProgress ? "IDEMPOTENCY_IN_PROGRESS" : "IDEMPOTENCY_KEY_REUSED";
    }
}
