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
import { ORDER_LABEL, type OrderStatus } from "../api/types";

type HistoryEntry = {
  id: string;
  toStatus: string;
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

function activityFromHistory(entry: HistoryEntry): Activity {
  const failed = [
    "DELIVERY_FAILED",
    "DELIVERY_FAIL",
    "SHIPPING_FAILED",
  ].includes(entry.toStatus);
  const complete = ["DELIVERED", "COMPLETED"].includes(entry.toStatus);
  return {
    id: entry.id,
    title: failed
      ? "Giao hàng thất bại"
      : entry.toStatus === "PENDING"
        ? "Đã tạo đơn hàng"
        : ORDER_LABEL[entry.toStatus as OrderStatus] || entry.toStatus,
    description: entry.reason,
    timestamp: entry.createdAt,
    icon: failed
      ? AlertCircle
      : complete
        ? CheckCircle2
        : entry.toStatus === "SHIPPING"
          ? Truck
          : entry.toStatus === "PACKED" || entry.toStatus === "PROCESSING"
            ? Package
            : entry.toStatus === "CONFIRMED"
              ? ClipboardCheck
              : ShoppingBag,
    tone:
      failed || entry.toStatus === "CANCELLED"
        ? "danger"
        : complete
          ? "success"
          : undefined,
  };
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
