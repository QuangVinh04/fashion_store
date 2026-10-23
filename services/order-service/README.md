# Order Service

Sở hữu giỏ hàng, checkout, đơn hàng, khuyến mãi, vận chuyển và yêu cầu trả hàng. Database riêng: order_database; port 8089. Client đi qua BFF/gateway.

## Cấu trúc code

Tuân theo [cấu trúc Backend](../../docs/backend-code-structure.md): chia theo feature (Spring Modulith) — `cart`, `checkout`, `order`, `saga` (đặt hàng + hủy), `returns`, `shipment` (GHN), `promotion`, `dashboard`, và `shared` (outbox, Rabbit config, security, client Catalog/Identity, `OrderErrorCode`). Trong mỗi feature: entity/repository giữ dữ liệu; service giữ nghiệp vụ; mapper chuyển đổi dữ liệu; client gọi Catalog/Identity/Payment/GHN; event giữ các điểm vào message và lịch chạy.

- CartServiceImpl/CheckoutServiceImpl điều phối, CartTransactionService/CheckoutTransactionService giữ transaction ngắn quanh phần dữ liệu.
- OrderSagaEventListener nhận RabbitMQ; OrderSagaService xử lý nghiệp vụ; SagaReplyProcessor/SagaOutbox dùng lại cơ chế inbox/outbox hiện có.
- OrderCancellationService điều phối hủy; OrderCancellationSagaService ghi tiến trình trong OrderSaga(type=CANCELLATION); OrderCancellationViewService bổ sung quyền thao tác và dữ liệu hủy cho response.
- ReturnServiceImpl giữ flow yêu cầu → duyệt → nhận/QC → hoàn ví hoặc từ chối. ReturnRequestMapper chỉ ánh xạ response và danh sách ảnh đã lưu. Hoàn ví ghi intent và command vào outbox cùng transaction; OrderFundsRefundEventListener nhận kết quả Payment để đóng REFUNDED. Xem [flow hoàn ví qua queue](../../docs/return-refund-queue.md).
- ShipmentServiceImpl điều phối vận đơn/webhook; ShipmentCreationTransactionService quản lý các transaction tạo vận đơn; GhnClient gọi và xác minh dữ liệu nhà vận chuyển.
- Outbox (OutboxEvent, repository, recorder, relay) nằm trong `shared/outbox`; SagaOutbox nằm trong `saga/event`.
- `ModularityTest` + `src/test/resources/modulith-known-violations.txt` đóng băng coupling hiện có giữa order/saga/returns/shipment/checkout; vi phạm mới làm test fail.

## Dữ liệu chi tiết trả hàng và hoàn tiền

Khách không được hủy yêu cầu trả hàng sau khi gửi, kể cả trước khi admin duyệt.
Không có endpoint khách `POST /api/v1/orders/{id}/return-request/cancel`.
Luồng khách: gửi yêu cầu → chờ duyệt → lấy hàng trả → nhận/kiểm tra → hoàn tiền
(hoặc admin từ chối). Việc admin hủy và hệ thống đóng do quá hạn lấy hàng là
các thao tác riêng, vẫn giữ kiểm tra trạng thái và xác nhận GHN hiện có.

`GET /api/v1/orders/{id}/return-request` trả yêu cầu của người đang đăng nhập.
`id` là mã yêu cầu; `orderId`/`orderCode` liên kết đơn gốc. `reason`, `createdAt`,
`approvedAt`, `receivedAt` lần lượt là lý do, thời điểm yêu cầu, duyệt và nhận hàng.
Danh sách sản phẩm vẫn lấy từ API chi tiết đơn hàng.

Các field bổ sung chỉ là dữ liệu đọc, không thay đổi enum workflow hay schema DB:

| Field | Ý nghĩa |
| --- | --- |
| `refundStatus` | `NOT_STARTED`, `PROCESSING`, `COMPLETED`, `REVIEW_REQUIRED` |
| `refundDestination` | `FASHION_STORE_WALLET` theo chính sách hoàn vào ví hiện tại; không phải phương thức thanh toán gốc |
| `expectedRefundAmount` | Số tiền đã duyệt nếu có, nếu chưa có thì ước tính theo tổng đơn theo chính sách hoàn toàn bộ; không bảo đảm yêu cầu sẽ được duyệt |
| `approvedRefundAmount` | Giữ nguyên field cũ; có thể null trước khi bắt đầu hoàn |
| `actualRefundAmount` | Số tiền đã được xác nhận hoàn; null khi chưa có xác nhận |
| `currency` | Đơn vị tiền của đơn gốc; không tự suy đoán cho dữ liệu thiếu |
| `refundedAt` | Thời điểm order-service ghi nhận xác nhận hoàn thành; không phải giờ ghi có chính xác tại ví |
| `returnShippingProvider` | `GHN` khi có vận đơn trả hàng; null nếu chưa có |
| `returnTrackingCode` / `returnShipmentStatus` | Mã và trạng thái vận đơn chiều về; độc lập với vận đơn giao hàng ban đầu |

`refundStatus` được tính từ dữ liệu đang lưu. `REFUNDED` cùng mã bút toán ví
không rỗng là `COMPLETED`; chỉ khi đó mới trả `actualRefundAmount` và
`refundedAt` (nếu các dữ liệu tương ứng có sẵn). Bằng chứng hoàn tiền thiếu hoặc
không nhất quán, lỗi hoàn tiền và các trạng thái tài chính legacy chưa xác minh
(`REFUND_PENDING`, `LEGACY_REVIEW_REQUIRED`) trả `REVIEW_REQUIRED`.
Có `refundStartedAt` mà chưa có kết quả/lỗi thì trả `PROCESSING`; còn lại
`NOT_STARTED`. Xác nhận hoàn thành ưu tiên hơn thông báo lỗi cũ.

Frontend giữ `status` để hiển thị tiến trình trả hàng và dùng `refundStatus`
cho bước hoàn tiền. `REVIEW_REQUIRED` hiển thị “Đang kiểm tra kết quả hoàn tiền”,
không khẳng định tiền chưa về và không tự gửi lại lệnh hoàn. `closedAt` của yêu
cầu bị hủy/từ chối không được hiển thị thành thời điểm hoàn tiền.

## API danh sách đơn mua

`GET /api/v1/orders` hỗ trợ `page`, `size`, `status` (tương thích API cũ),
`tab` và `q` (tối đa 120 ký tự, tìm theo mã đơn hoặc tên sản phẩm đã đặt).
Các bộ lọc được kết hợp và luôn giới hạn theo tài khoản đang đăng nhập.

| `tab` | Nội dung |
| --- | --- |
| Bỏ trống | Tất cả đơn của khách |
| `PENDING` | Đơn đang xử lý, không đồng nghĩa chắc chắn chưa thanh toán |
| `PREPARING` | `CONFIRMED`, `PROCESSING`, `PACKED` |
| `SHIPPING` | `SHIPPING` |
| `COMPLETED` | `DELIVERED`, `COMPLETED` |
| `CANCELLED` | `CANCELLED` |
| `RETURNS` | Đơn có ca trả hàng/hoàn tiền `CUSTOMER_RETURN`, `CARRIER_RETURN` hoặc `VERIFIED_LOSS`, kể cả ca đã đóng |

Response khách bổ sung `items` (snapshot sản phẩm trong đơn), `returnRequestId`
và `canRequestReturn`. Quyền trả hàng dùng chung chính sách của trang chi tiết.
BE phân trang trên đơn trước, sau đó nạp item và mã yêu cầu trả hàng theo các ID
trong trang; không fetch collection với Pageable, không gọi truy vấn cho từng đơn.
Tìm kiếm dùng EXISTS nên nhiều dòng sản phẩm khớp không làm trùng đơn/sai tổng trang.
Ảnh sản phẩm vẫn lấy từ catalog hiện tại. Danh sách admin giữ response rút gọn.

## Kiểm thử

Unit/controller/security tests chạy bằng Maven với Java 21. PostgreSQL integration và các contract dùng hạ tầng thật cần Docker. Sau đổi package, chạy build sạch hoặc dùng bản sao source không có target cũ.

Xem [hướng dẫn chạy test](../../docs/backend-test-guide.md) để chạy cả integration H2 và tách các lớp PostgreSQL/RabbitMQ bằng tag.

Các lỗi nghiệp vụ còn tồn tại được theo dõi tại [báo cáo tài chính và trả hàng](../../docs/reviews/2026-10-12-payment-wallet-return-audit.md); việc chuẩn hóa cấu trúc không thay thế bước sửa các lỗi đó.

Hủy đơn đã xác nhận dùng OrderSaga(type=CANCELLATION), riêng với CHECKOUT. Flow chi tiết, retry, API và cutover: [cancellation-ordersaga](../../docs/cancellation-ordersaga.md). OrderCancellation entity/table và fulfillment_blocked_at đã được thay bằng Saga và Order.status=CANCELLING.
