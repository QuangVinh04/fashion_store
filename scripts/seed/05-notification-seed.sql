-- 05-notification-seed.sql
-- Seed dữ liệu cho notification_database (Port: 5437 / Service: notification-service)
-- Mỗi bảng có từ 12 dòng dữ liệu, đồng bộ User UUID và Order ID.
BEGIN;
SET client_encoding = 'UTF8';

-- 1. In-App Notifications (12 thông báo đa dạng: đơn hàng, giao hàng, hoàn tiền, voucher)
INSERT INTO in_app_notification (
    id, user_id, type, title, message, order_id, read_at, created_at, updated_at
) VALUES
    ('notif-01', '00000000-0000-0000-0000-000000000011', 'ORDER_COMPLETED',
     'Đơn hàng hoàn tất', 'Đơn hàng ORD-2026-0001 của bạn đã được giao thành công và hoàn tất. Cảm ơn bạn đã mua sắm tại LINO Fashion!', 'ord-01',
     now() - interval '4 days', now() - interval '4 days', now()),

    ('notif-02', '00000000-0000-0000-0000-000000000012', 'ORDER_COMPLETED',
     'Đơn hàng hoàn tất', 'Đơn hàng ORD-2026-0002 của bạn đã được giao thành công. Đừng quên đánh giá sản phẩm để nhận ưu đãi nhé!', 'ord-02',
     now() - interval '3 days', now() - interval '3 days', now()),

    ('notif-03', '00000000-0000-0000-0000-000000000013', 'ORDER_DELIVERED',
     'Đơn hàng đã được giao', 'Đơn hàng ORD-2026-0003 đã được bưu tá GHN giao thành công đến bạn. Vui lòng kiểm tra kiện hàng.', 'ord-03',
     now() - interval '1 day', now() - interval '1 day', now()),

    ('notif-04', '00000000-0000-0000-0000-000000000014', 'ORDER_SHIPPED',
     'Đơn hàng đang trên đường giao', 'Kiện hàng của đơn ORD-2026-0004 đã được bàn giao cho đơn vị vận chuyển GHN (Mã: GHN-ORD-0004).', 'ord-04',
     null, now() - interval '1 day', now()),

    ('notif-05', '00000000-0000-0000-0000-000000000015', 'ORDER_PROCESSING',
     'Đơn hàng đang được chuẩn bị', 'Đơn hàng ORD-2026-0005 đang được nhân viên kho đóng gói cẩn thận trước khi gửi đi.', 'ord-05',
     null, now() - interval '18 hours', now()),

    ('notif-06', '00000000-0000-0000-0000-000000000016', 'ORDER_CONFIRMED',
     'Đặt hàng thành công', 'Đơn hàng ORD-2026-0006 (COD) đã được xác nhận. Chúng tôi sẽ sớm giao hàng đến bạn.', 'ord-06',
     null, now() - interval '12 hours', now()),

    ('notif-07', '00000000-0000-0000-0000-000000000017', 'ORDER_PENDING',
     'Chờ thanh toán đơn hàng', 'Đơn hàng ORD-2026-0007 đang chờ bạn hoàn tất thanh toán qua cổng PayOS.', 'ord-07',
     null, now() - interval '2 hours', now()),

    ('notif-08', '00000000-0000-0000-0000-000000000018', 'ORDER_CANCELLED',
     'Đơn hàng đã hủy', 'Đơn hàng ORD-2026-0008 đã được hủy theo yêu cầu của bạn.', 'ord-08',
     now() - interval '7 days', now() - interval '7 days', now()),

    ('notif-09', '00000000-0000-0000-0000-000000000019', 'REFUND_SUCCESS',
     'Hoàn tiền thành công', 'Yêu cầu trả hàng cho đơn ORD-2026-0009 đã được phê duyệt. Số tiền 824.000 đ đã được cộng vào ví thời trang của bạn.', 'ord-09',
     now() - interval '10 days', now() - interval '10 days', now()),

    ('notif-10', '00000000-0000-0000-0000-000000000020', 'ORDER_COMPLETED',
     'Đơn hàng hoàn tất', 'Đơn hàng ORD-2026-0010 của bạn đã hoàn tất thành công. Chúc bạn có trải nghiệm tuyệt vời cùng LINO!', 'ord-10',
     now() - interval '1 day', now() - interval '1 day', now()),

    ('notif-11', '00000000-0000-0000-0000-000000000011', 'PROMOTION_NEW',
     'Ưu đãi độc quyền', 'Mã giảm giá LINO10 giảm 10% cho các sản phẩm áo sơ mi Oxford vừa được lưu vào ví voucher của bạn.', 'ord-01',
     now() - interval '7 days', now() - interval '7 days', now()),

    ('notif-12', '00000000-0000-0000-0000-000000000002', 'ORDER_COMPLETED',
     'Đơn hàng mẫu hoàn tất', 'Đơn hàng ORD-2026-0012 đã giao thành công tới Khách Hàng Mẫu.', 'ord-12',
     now() - interval '8 days', now() - interval '8 days', now())
ON CONFLICT (id) DO NOTHING;

COMMIT;
