CREATE TABLE sales (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    total NUMERIC(14, 2) NOT NULL,
    notes VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sales PRIMARY KEY (id),
    CONSTRAINT ck_sales_total_non_negative CHECK (total >= 0)
);

CREATE TABLE sale_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    product_sku VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL,
    unit_cost NUMERIC(12, 2) NOT NULL,
    subtotal NUMERIC(14, 2) NOT NULL,
    CONSTRAINT pk_sale_items PRIMARY KEY (id),
    CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id)
        REFERENCES sales (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_items_product FOREIGN KEY (product_id)
        REFERENCES products (id) ON DELETE RESTRICT,
    CONSTRAINT uq_sale_items_sale_product UNIQUE (sale_id, product_id),
    CONSTRAINT ck_sale_items_product_name_not_blank CHECK (BTRIM(product_name) <> ''),
    CONSTRAINT ck_sale_items_product_sku_not_blank CHECK (BTRIM(product_sku) <> ''),
    CONSTRAINT ck_sale_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_sale_items_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_sale_items_unit_cost_non_negative CHECK (unit_cost >= 0),
    CONSTRAINT ck_sale_items_subtotal_non_negative CHECK (subtotal >= 0),
    CONSTRAINT ck_sale_items_subtotal_matches CHECK (subtotal = unit_price * quantity)
);

CREATE INDEX idx_sales_created_at
    ON sales (created_at DESC, id DESC);

CREATE INDEX idx_sale_items_product_id
    ON sale_items (product_id);
