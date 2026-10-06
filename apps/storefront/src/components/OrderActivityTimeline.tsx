import { useState } from "react";
import {
  AlertCircle,
  CheckCircle2,
  ClipboardCheck,
  Package,
  ShoppingBag,
  Truck,
  type LucideIcon,
} from "lucide-react";

type HistoryEntry = {
  id: string;
  toStatus: string;
  action?: string;
  reason?: string;
  createdAt: string;
};
type Activity = {
  id: string;
  title: string;
  description?: string;
  timestamp: string;
  icon: LucideIcon;
  tone?: "danger" | "success";
};

type ActivityContent = Pick<Activity, "title" | "description" | "icon" | "tone">;

const statusContent: Record<string, ActivityContent> = {
  PENDING: { title: "Đã đặt hàng", description: "Đơn hàng của bạn đã được ghi nhận.", icon: ShoppingBag },
  CONFIRMED: { title: "Đã xác nhận", description: "Đơn hàng đã được xác nhận và sẽ được shop chuẩn bị.", icon: ClipboardCheck },
  PROCESSING: { title: "Đang chuẩn bị hàng", description: "Shop đang chuẩn bị sản phẩm của bạn.", icon: Package },
  PACKED: { title: "Đã đóng gói", description: "Đơn hàng đã được đóng gói và đang chờ giao cho đơn vị vận chuyển.", icon: Package },
  SHIPPING: { title: "Đang vận chuyển", description: "Đơn hàng đang trên đường đến bạn.", icon: Truck },
  DELIVERED: { title: "Đã giao hàng", description: "Đơn hàng đã được giao thành công.", icon: CheckCircle2, tone: "success" },
  COMPLETED: { title: "Đơn hàng hoàn tất", description: "Cảm ơn bạn đã mua sắm tại shop.", icon: CheckCircle2, tone: "success" },
  CANCELLED: { title: "Đã hủy đơn hàng", description: "Đơn hàng đã được hủy.", icon: AlertCircle, tone: "danger" },
  RETURNED: { title: "Đã cập nhật trả hàng", description: "Đơn hàng đã được cập nhật sang trạng thái trả hàng.", icon: Package },
  REFUNDED: { title: "Đã hoàn tiền", description: "Khoản thanh toán của đơn hàng đã được hoàn lại.", icon: CheckCircle2, tone: "success" },
};

const deliveryFailed: ActivityContent = {
  title: "Giao hàng chưa thành công", description: "Đơn hàng chưa được giao thành công. Vui lòng liên hệ shop để được hỗ trợ.", icon: AlertCircle, tone: "danger",
};
const eventContent: Record<string, ActivityContent> = {
  ORDER_CREATED: statusContent.PENDING,
  ORDER_CONFIRMED: statusContent.CONFIRMED,
  ORDER_CANCELLED: statusContent.CANCELLED,
  SAGA_TIMEOUT: { ...statusContent.CANCELLED, description: "Đơn hàng đã được hủy do quá thời gian xử lý. Vui lòng liên hệ shop nếu bạn cần hỗ trợ." },
  SHIPMENT_CREATED: { title: "Đã tạo vận đơn", description: "Đơn hàng đang chờ đơn vị vận chuyển lấy hàng.", icon: Truck },
  RETURN_REQUESTED: { title: "Đã gửi yêu cầu trả hàng", description: "Shop đã nhận yêu cầu trả hàng của bạn và sẽ xem xét.", icon: Package },
  RETURN_APPROVED: { title: "Yêu cầu trả hàng đã được chấp nhận", description: "Shop đã chấp nhận yêu cầu trả hàng của bạn.", icon: ClipboardCheck },
  RETURN_REJECTED: { title: "Yêu cầu trả hàng chưa được chấp nhận", description: "Vui lòng xem chi tiết yêu cầu trả hàng hoặc liên hệ shop để được hỗ trợ.", icon: AlertCircle, tone: "danger" },
  PAYMENT_REFUNDED: { title: "Đã hoàn tiền", description: "Khoản thanh toán đã được hoàn qua cổng thanh toán.", icon: CheckCircle2, tone: "success" },
  PAYMENT_REFUND_FAILED: { title: "Chưa hoàn tiền thành công", description: "Shop cần kiểm tra lại việc hoàn tiền. Vui lòng liên hệ shop để được hỗ trợ.", icon: AlertCircle, tone: "danger" },
};

function activityFromHistory(entry: HistoryEntry): Activity {
  // Raw reasons belong to the audit history and may contain internal diagnostics.
  // Actions distinguish events such as return rejection from an unchanged order status.
  const event = entry.action && Object.hasOwn(eventContent, entry.action)
    ? eventContent[entry.action] : undefined;
  const useStatus = !entry.action || ["ADMIN_UPDATE", "GHN_WEBHOOK"].includes(entry.action);
  const status = useStatus
    ? ["DELIVERY_FAILED", "DELIVERY_FAIL", "SHIPPING_FAILED"].includes(entry.toStatus)
      ? deliveryFailed
      : Object.hasOwn(statusContent, entry.toStatus) ? statusContent[entry.toStatus] : undefined
    : undefined;
  const content = event || status || {
    title: "Đơn hàng đã được cập nhật", description: "Thông tin đơn hàng vừa được cập nhật.", icon: ShoppingBag,
  };
  return { ...content, id: entry.id, timestamp: entry.createdAt };
}

function timestampLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Chưa có thời gian";
  const time = date.toLocaleTimeString("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
  });
  return `${time} · ${date.toLocaleDateString("vi-VN")}`;
}

export default function OrderActivityTimeline({
  history,
  loading,
  error,
  retry,
}: {
  history: HistoryEntry[] | null;
  loading: boolean;
  error: string;
  retry: () => void;
}) {
  const [visible, setVisible] = useState(10);
  const activities = [...(history || [])]
    .sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    )
    .map(activityFromHistory);

  return (
    <div>
      {loading ? (
        <p role="status" className="text-sm text-muted-foreground">
          Đang tải lịch sử đơn hàng…
        </p>
      ) : error ? (
        <div role="alert" className="rounded-xl bg-primary-light p-3 text-sm">
          <p>Không tải được lịch sử đơn hàng.</p>
          <button
            type="button"
            onClick={retry}
            className="mt-2 store-button px-3 font-medium"
          >
            Thử lại
          </button>
        </div>
      ) : activities.length ? (
        <>
          <ol aria-label="Hoạt động đơn hàng" className="text-sm">
            {activities.slice(0, visible).map((activity, index) => {
              const Icon = activity.icon;
              return (
                <li
                  key={activity.id}
                  className="relative flex gap-3 pb-6 last:pb-0"
                >
                  {index < Math.min(activities.length, visible) - 1 && (
                    <span
                      aria-hidden="true"
                      className="absolute top-10 bottom-1 left-[15px] w-0.5 bg-border"
                    />
                  )}
                  <span
                    aria-hidden="true"
                    className={`grid size-8 shrink-0 place-items-center rounded-full ${activity.tone === "danger" ? "bg-primary-light text-destructive" : activity.tone === "success" ? "bg-success-light text-success" : "bg-background text-muted-foreground"}`}
                  >
                    <Icon className="size-4" />
                  </span>
                  <div className="min-w-0 flex-1 pt-1.5">
                    <div className="flex flex-col gap-0.5 sm:flex-row sm:items-baseline sm:justify-between sm:gap-4">
                      <p className="font-medium text-foreground">
                        {activity.title}
                      </p>
                      <time
                        dateTime={activity.timestamp}
                        className="shrink-0 whitespace-nowrap text-xs text-muted-foreground tabular-nums"
                      >
                        {timestampLabel(activity.timestamp)}
                      </time>
                    </div>
                    {activity.description && (
                      <p className="mt-1 max-w-[55ch] text-pretty text-muted-foreground">
                        {activity.description}
                      </p>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
          {activities.length > visible && (
            <button
              type="button"
              onClick={() => setVisible((count) => count + 10)}
              className="ml-8 mt-4 store-button px-3 text-sm font-medium"
            >
              Xem hoạt động cũ hơn
            </button>
          )}
        </>
      ) : (
        <p className="text-sm text-muted-foreground">
          Chưa có hoạt động nào được ghi nhận.
        </p>
      )}
    </div>
  );
}
