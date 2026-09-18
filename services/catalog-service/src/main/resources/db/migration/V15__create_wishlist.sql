CREATE TABLE IF NOT EXISTS wishlist_item (
    user_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_wishlist_item PRIMARY KEY (user_id, product_id),
    CONSTRAINT fk_wishlist_item_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_wishlist_user_id ON wishlist_item(user_id);
