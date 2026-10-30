# Luồng thanh toán

Xử lý: `payment-service`. Bắt đầu khi nhận `payment.requested` từ checkout saga (`docs/flows/checkout-saga.md`).

## COD

1. payment-service tạo payment trạng thái `COD_PENDING` và phát `payment.completed` ngay. Saga đi tiếp.
2. GHN giao hàng thành công. order-service phát `order.delivered` kèm bằng chứng thu tiền: số tiền, thời điểm, mã vận đơn, shop GHN.
3. payment-service kiểm tra bằng chứng (đúng số tiền, VND, mã hợp lệ) và chuyển payment `COMPLETED`.

## Online (VNPay, PayOS)

1. payment-service tạo payment `PENDING`, gán sẵn mã giao dịch phía provider.
2. Khách gọi `POST /api/v1/payments/{id}/initiate` để lấy link. payment-service phát `payment.initiated` kèm `paymentUrl`.
   - Mã giao dịch cố định từ đầu. Tạo lại link dùng đúng mã cũ, nên provider không sinh giao dịch thứ hai.
   - Quá deadline saga thì không phát link mới.
3. Provider gọi lại:
   - VNPay: `GET /api/v1/payments/vnpay/ipn` (server) và `/return` (browser).
   - PayOS: `POST /api/v1/payments/payos/webhook` (server) và `/return` (browser).
4. Callback hợp lệ → `payment.completed` hoặc `payment.failed`. Callback kiểm tra chữ ký, số tiền, trạng thái đã xử lý.

## Ví (WALLET, provider INTERNAL)

1. payment-service tạo payment `PENDING`.
2. Khách gọi `POST /api/v1/payments/{id}/initiate`. payment-service phát `payment.initiated` với `paymentUrl` trỏ tới trang `/checkout/wallet-pay?paymentId=...` của storefront.
3. Trang này gọi `GET /api/v1/payments/{id}/wallet-session`, rồi khách xác nhận bằng `POST /api/v1/payments/{id}/wallet-confirm`.
4. Trừ ví, ghi sổ cái, phát `payment.completed`.

## Hủy payment

`payment.cancellation.requested`:
- Payment chưa trả → `CANCELLED`, phát `payment.cancelled`.
- Payment đã trả → phát `payment.cancellation.rejected`.

## Hoàn tiền

Mọi khoản hoàn tiền đều ghi có vào ví người mua (`OrderFundsRefundService`), dùng chung lock payment và sổ cái.

| command | dùng khi | reply |
|---|---|---|
| `wallet.saga-compensation.requested` | saga checkout bù trừ đơn trả bằng ví | `wallet.saga-compensation.completed` / `.rejected` |
| `payment.order-funds-refund.requested` | bù trừ đơn online, hủy đơn đã trả, đổi trả | `payment.order-funds-refund.credited` / `.rejected` |

## Nạp ví

`POST /api/v1/wallet/top-ups` tạo payment loại `TOP_UP`. Theo dõi bằng `GET /api/v1/wallet/top-ups/{id}`.
