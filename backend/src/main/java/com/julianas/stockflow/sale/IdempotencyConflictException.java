package com.julianas.stockflow.sale;

public class IdempotencyConflictException extends RuntimeException {
    private final boolean inProgress;

    public IdempotencyConflictException(boolean inProgress) {
        super(inProgress ? "A confirmation with this key is still in progress. Retry with the same key."
                : "This key was already used for a different sale request.");
        this.inProgress = inProgress;
    }

    public String getCode() {
        return inProgress ? "IDEMPOTENCY_IN_PROGRESS" : "IDEMPOTENCY_KEY_REUSED";
    }
}
