-- Vận đơn chiều về (khách -> kho) của một yêu cầu trả hàng. Tách khỏi bảng shipment vì shipment
-- là 1-1 với order (UNIQUE order_id) và webhook GHN coi mọi mã GHN trong đó là chiều giao đi.
CREATE TABLE return_shipment (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    return_request_id VARCHAR(36) NOT NULL,
    ghn_order_code VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_return_shipment_return_request UNIQUE (return_request_id),
    CONSTRAINT uk_return_shipment_ghn_order_code UNIQUE (ghn_order_code),
    CONSTRAINT fk_return_shipment_return_request FOREIGN KEY (return_request_id) REFERENCES return_request (id) ON DELETE CASCADE
);
