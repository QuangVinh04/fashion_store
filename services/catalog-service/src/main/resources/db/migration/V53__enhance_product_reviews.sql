ALTER TABLE review
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'VISIBLE',
    ADD COLUMN IF NOT EXISTS reply_comment TEXT,
    ADD COLUMN IF NOT EXISTS replied_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS replied_by VARCHAR(36),
    ADD COLUMN IF NOT EXISTS fit_rating VARCHAR(20),
    ADD COLUMN IF NOT EXISTS customer_height INT,
    ADD COLUMN IF NOT EXISTS customer_weight INT;

CREATE TABLE IF NOT EXISTS review_media (
    id VARCHAR(36) PRIMARY KEY,
    review_id VARCHAR(36) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    media_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_review_media_review FOREIGN KEY (review_id) REFERENCES review(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_review_media_review_id ON review_media(review_id);
CREATE INDEX IF NOT EXISTS idx_review_status ON review(status);
CREATE INDEX IF NOT EXISTS idx_review_product_status ON review(product_id, status);
