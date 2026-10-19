-- Lượt lấy hàng trả thất bại (GHN hủy / quá hạn) giữ phiếu mở để admin tạo lại hoặc hủy:
-- lưu lý do lần lỗi gần nhất và mốc đặt lượt lấy hàng hiện tại (tính quá hạn theo lượt, không theo ngày duyệt).
ALTER TABLE return_shipment
    ADD COLUMN failure_code VARCHAR(40),
    ADD COLUMN failure_reason VARCHAR(500),
    ADD COLUMN failed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN pickup_requested_at TIMESTAMP WITH TIME ZONE;
