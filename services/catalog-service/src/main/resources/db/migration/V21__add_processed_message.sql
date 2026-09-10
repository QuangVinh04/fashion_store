CREATE TABLE IF NOT EXISTS processed_message (
    id VARCHAR(255) PRIMARY KEY,
    message_id VARCHAR(255) NOT NULL,
    consumer_name VARCHAR(120) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT uk_processed_message UNIQUE (message_id, consumer_name)
);

CREATE INDEX IF NOT EXISTS idx_processed_message_consumer
    ON processed_message (consumer_name, processed_at);
