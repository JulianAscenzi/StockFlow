CREATE TABLE stock_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    product_id BIGINT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    stock_before INTEGER NOT NULL,
    stock_after INTEGER NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_stock_movements PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT ck_stock_movements_type CHECK (movement_type IN ('IN', 'OUT')),
    CONSTRAINT ck_stock_movements_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_stock_movements_stock_before_non_negative CHECK (stock_before >= 0),
    CONSTRAINT ck_stock_movements_stock_after_non_negative CHECK (stock_after >= 0),
    CONSTRAINT ck_stock_movements_reason_not_blank CHECK (BTRIM(reason) <> ''),
    CONSTRAINT ck_stock_movements_balance CHECK (
        (movement_type = 'IN' AND stock_after = stock_before + quantity)
        OR (movement_type = 'OUT' AND stock_after = stock_before - quantity)
    )
);

CREATE INDEX idx_stock_movements_product_created_at
    ON stock_movements (product_id, created_at DESC, id DESC);
