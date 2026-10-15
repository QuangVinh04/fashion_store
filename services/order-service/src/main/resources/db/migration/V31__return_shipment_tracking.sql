-- Theo dõi vòng đời vận đơn chiều về: mốc lấy hàng / tới kho / hủy, trạng thái GHN cuối cùng,
-- số lần thử hủy khi quá hạn lấy hàng và lần đối soát gần nhất (webhook GHN có thể bị lỡ).
ALTER TABLE return_shipment
    ADD COLUMN picked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN arrived_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN cancelled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_ghn_status VARCHAR(40),
    ADD COLUMN cancel_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_reconciled_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_return_shipment_open ON return_shipment (status, last_reconciled_at);
