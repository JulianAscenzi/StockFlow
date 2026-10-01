CREATE TABLE sale_confirmations (
    idempotency_key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    sale_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sale_confirmations PRIMARY KEY (idempotency_key),
    CONSTRAINT uq_sale_confirmations_sale UNIQUE (sale_id),
    CONSTRAINT ck_sale_confirmations_hash CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT fk_sale_confirmations_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT
);
