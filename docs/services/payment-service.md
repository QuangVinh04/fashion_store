# payment-service

Cổng 8085 · database `payment_database` (host 5433) · feature-first.

## Feature

| feature | trách nhiệm |
|---|---|
| `payment` | thanh toán đơn và nạp ví: COD, online (VNPay, PayOS), ví. Callback cổng thanh toán. Hoàn tiền đơn. Bồi hoàn saga ví |
| `wallet` | tài khoản ví và sổ cái (`wallet_account`, `wallet_ledger_entry`) |

`PaymentMethod` → `PaymentProvider`: `COD` → `COD`, `ONLINE` → `VNPAY` / `PAYOS`, `WALLET` → `INTERNAL`.
`PaymentType`: `ORDER`, `TOP_UP`.
`PaymentStatus`: `PENDING`, `COD_PENDING`, `COMPLETED`, `FAILED`, `CANCELLED`, `REFUNDED`. `INITIATING` / `INITIATION_UNKNOWN` chỉ còn ở dữ liệu cũ.

Mọi khoản hoàn tiền đơn đều ghi có vào ví của người mua.

## API chính

| nhóm | path |
|---|---|
| thanh toán đơn | `GET /api/v1/payments/order/{orderId}`, `POST /api/v1/payments/{id}/initiate`, `GET /api/v1/payments/resolve-return` |
| trả tiền bằng ví | `GET /api/v1/payments/{id}/wallet-session`, `POST /api/v1/payments/{id}/wallet-confirm` |
| callback VNPay | `GET /api/v1/payments/vnpay/return`, `GET /api/v1/payments/vnpay/ipn` |
| callback PayOS | `GET /api/v1/payments/payos/return`, `POST /api/v1/payments/payos/webhook` |
| ví (khách) | `GET /api/v1/wallet`, `GET /api/v1/wallet/transactions`, `POST /api/v1/wallet/top-ups`, `GET /api/v1/wallet/top-ups/{id}` |
| admin | `/api/v1/admin/payments/**`, `/api/v1/admin/wallets/**` |

## Messaging

| nhận | xử lý | trả lời |
|---|---|---|
| `payment.requested` | tạo payment | `payment.completed` (COD ngay lập tức), `payment.initiated`, `payment.completed` / `payment.failed` sau callback |
| `payment.cancellation.requested` | hủy payment chưa trả | `payment.cancelled` / `payment.cancellation.rejected` |
| `wallet.saga-compensation.requested` | hoàn tiền ví trong saga checkout | `wallet.saga-compensation.completed` / `.rejected` |
| `payment.order-funds-refund.requested` | hoàn tiền đơn vào ví | `payment.order-funds-refund.credited` / `.rejected` |
| `order.delivered` | đơn COD có bằng chứng thu tiền GHN hợp lệ: chuyển payment `COMPLETED` | — |

## HTTP ra ngoài

VNPay, PayOS.

## Bảng chính

`payment`, `payment_refund`, `wallet_account`, `wallet_ledger_entry`, `outbox_event`, `processed_message`.
