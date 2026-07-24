CREATE TABLE IF NOT EXISTS outbox_event (
    id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(300) NOT NULL,
    routing_key VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    last_error VARCHAR(1000),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_outbox_event_publishable
    ON outbox_event (status, next_attempt_at, created_at);
