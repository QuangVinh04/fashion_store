# Order Service

Sở hữu giỏ hàng, checkout, đơn hàng, khuyến mãi, vận chuyển và yêu cầu trả hàng. Database riêng: order_database; port 8089. Client đi qua BFF/gateway.

## Cấu trúc code

Tuân theo [cấu trúc Backend](../../docs/backend-code-structure.md). Entity/repository giữ dữ liệu; service giữ nghiệp vụ; mapper chuyển đổi dữ liệu; client gọi Catalog/Identity/Payment/GHN; event giữ các điểm vào message và lịch chạy.

- CartServiceImpl/CheckoutServiceImpl điều phối, CartTransactionService/CheckoutTransactionService giữ transaction ngắn quanh phần dữ liệu.
- OrderSagaEventListener nhận RabbitMQ; OrderSagaService xử lý nghiệp vụ; SagaReplyProcessor/SagaOutbox dùng lại cơ chế inbox/outbox hiện có.
- OrderCancellationService điều phối hủy; OrderCancellationSagaService ghi tiến trình trong OrderSaga(type=CANCELLATION); OrderCancellationViewService bổ sung quyền thao tác và dữ liệu hủy cho response.
- ReturnServiceImpl giữ flow yêu cầu → duyệt → nhận/QC → hoàn ví hoặc từ chối. ReturnRequestMapper chỉ ánh xạ response và danh sách ảnh đã lưu. Hoàn ví ghi intent và command vào outbox cùng transaction; OrderFundsRefundEventListener nhận kết quả Payment để đóng REFUNDED. Xem [flow hoàn ví qua queue](../../docs/return-refund-queue.md).
- ShipmentServiceImpl điều phối vận đơn/webhook; ShipmentCreationTransactionService quản lý các transaction tạo vận đơn; GhnClient gọi và xác minh dữ liệu nhà vận chuyển.
- OutboxEvent nằm trong entity, repository tương ứng trong repository; OutboxEventRecorder/OutboxEventRelay nằm trong event.

## Kiểm thử

Unit/controller/security tests chạy bằng Maven với Java 21. PostgreSQL integration và các contract dùng hạ tầng thật cần Docker. Sau đổi package, chạy build sạch hoặc dùng bản sao source không có target cũ.

Xem [hướng dẫn chạy test](../../docs/backend-test-guide.md) để chạy cả integration H2 và tách các lớp PostgreSQL/RabbitMQ bằng tag.

Các lỗi nghiệp vụ còn tồn tại được theo dõi tại [báo cáo tài chính và trả hàng](../../docs/reviews/2026-10-12-payment-wallet-return-audit.md); việc chuẩn hóa cấu trúc không thay thế bước sửa các lỗi đó.

Hủy đơn đã xác nhận dùng OrderSaga(type=CANCELLATION), riêng với CHECKOUT. Flow chi tiết, retry, API và cutover: [cancellation-ordersaga](../../docs/cancellation-ordersaga.md). OrderCancellation entity/table và fulfillment_blocked_at đã được thay bằng Saga và Order.status=CANCELLING.
