CREATE TABLE inventory_confirmations (
    idempotency_key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    movement_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_inventory_confirmations PRIMARY KEY (idempotency_key),
    CONSTRAINT uq_inventory_confirmations_movement UNIQUE (movement_id),
    CONSTRAINT ck_inventory_confirmations_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT fk_inventory_confirmations_movement FOREIGN KEY (movement_id)
        REFERENCES stock_movements(id) ON DELETE RESTRICT
);
