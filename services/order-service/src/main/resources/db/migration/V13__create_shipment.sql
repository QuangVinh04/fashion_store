CREATE TABLE shipment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    tracking_code VARCHAR(100),
    ghn_order_code VARCHAR(100),
    to_district_id INTEGER,
    to_ward_code VARCHAR(20),
    fee NUMERIC(19, 2) NOT NULL DEFAULT 0,
    weight_gram INTEGER,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_shipment_order_id UNIQUE (order_id),
    CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_shipment_tracking_code ON shipment (tracking_code);
CREATE INDEX idx_shipment_status ON shipment (status);
