CREATE TABLE categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_categories PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_categories_name_ci
    ON categories (LOWER(name));

CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(150) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    price NUMERIC(12, 2) NOT NULL,
    cost NUMERIC(12, 2) NOT NULL,
    stock INTEGER NOT NULL DEFAULT 0,
    minimum_stock INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    category_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT ck_products_cost_non_negative CHECK (cost >= 0),
    CONSTRAINT ck_products_stock_non_negative CHECK (stock >= 0),
    CONSTRAINT ck_products_minimum_stock_non_negative CHECK (minimum_stock >= 0),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id)
        REFERENCES categories (id) ON DELETE RESTRICT
);

CREATE INDEX idx_products_category_id
    ON products (category_id);

CREATE INDEX idx_products_name_ci
    ON products (LOWER(name));
