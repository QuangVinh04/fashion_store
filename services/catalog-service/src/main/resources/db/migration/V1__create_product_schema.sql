CREATE TABLE category (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    name VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    parent_id VARCHAR(255),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category(id)
);

CREATE TABLE product (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price NUMERIC(19, 2) NOT NULL,
    category_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id)
);

CREATE TABLE product_variant (
    id VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    product_id VARCHAR(255) NOT NULL,
    size VARCHAR(255),
    color VARCHAR(255),
    sku VARCHAR(255) UNIQUE,
    price NUMERIC(19, 2) NOT NULL,
    CONSTRAINT fk_product_variant_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

CREATE INDEX idx_product_category_id ON product(category_id);
CREATE INDEX idx_product_variant_product_id ON product_variant(product_id);
