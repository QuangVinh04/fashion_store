CREATE TABLE IF NOT EXISTS return_request (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    reason TEXT NOT NULL,
    images TEXT,
    status VARCHAR(30) NOT NULL,
    reject_reason VARCHAR(500),
    reviewed_by VARCHAR(120),
    reviewed_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_return_request_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_return_request_user_status ON return_request (user_id, status);
CREATE INDEX IF NOT EXISTS idx_return_request_status ON return_request (status);
