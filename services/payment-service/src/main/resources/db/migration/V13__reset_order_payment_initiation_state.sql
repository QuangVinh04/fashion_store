-- Payment đơn hàng không còn trạng thái khởi tạo trung gian: link do khách tạo đồng bộ qua
-- /initiate. Đưa các dòng đang kẹt về PENDING để khách tạo lại link hoặc saga huỷ được.
UPDATE payment
SET status = 'PENDING', failure_reason = NULL
WHERE type = 'ORDER'
  AND status IN ('INITIATING', 'INITIATION_UNKNOWN');
