import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Bell, ChevronLeft, ChevronRight } from 'lucide-react';
import { Button } from '../../app/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '../../app/components/ui/popover';
import { notificationLink, type StoreNotification } from '../../api/notifications';
import { useNotifications } from './useNotifications';

const date = (value: string) => new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
}).format(new Date(value));

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const navigate = useNavigate();
  const { snapshot, pageNo, setPageNo, loading, saving, error, connection, refresh, markRead } = useNotifications();
  const unread = snapshot?.unreadCount ?? 0;

  async function view(item: StoreNotification) {
    if (!item.readAt && !(await markRead(item.id))) return;
    setOpen(false);
    navigate(notificationLink(item.orderId));
  }

  return (
    <Popover open={open} onOpenChange={value => { setOpen(value); if (value) refresh(); }}>
      <PopoverTrigger asChild>
        <button className="store-icon-button relative" aria-label={`Thông báo${unread ? `, ${unread} chưa đọc` : ''}`}>
          <Bell size={19} />
          {unread > 0 && <span className="absolute right-0 top-0 flex min-w-4 items-center justify-center rounded-full bg-primary-light px-1 text-xs font-medium tabular-nums text-destructive">
            {unread > 99 ? '99+' : unread}
          </span>}
        </button>
      </PopoverTrigger>
      <PopoverContent align="end" sideOffset={12} collisionPadding={16}
        className="w-[min(400px,calc(100vw-32px))] rounded-xl border-border bg-card p-0">
        <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
          <h2 className="text-sm font-medium">Thông báo</h2>
          <Button variant="ghost" size="sm" disabled={!unread || saving} onClick={() => void markRead()}>
            Đọc tất cả
          </Button>
        </div>
        {connection !== 'connected' && <p className="px-4 pt-3 text-xs text-muted-foreground" role="status">
          {connection === 'connecting' ? 'Đang kết nối thông báo…' : 'Đang kết nối lại thông báo…'}
        </p>}
        {error && <div className="flex items-center justify-between gap-3 px-4 py-3" role="alert">
          <p className="text-sm text-destructive text-pretty">{error}</p>
          <Button variant="ghost" size="sm" onClick={refresh}>Thử lại</Button>
        </div>}
        <div className="relative max-h-[min(480px,60vh)] overflow-y-auto" aria-busy={loading}>
          {loading && snapshot && <div className="absolute inset-x-0 top-0 h-0.5 animate-pulse bg-foreground/20 motion-reduce:animate-none" />}
          {!snapshot && loading ? <div className="divide-y divide-border" role="status" aria-label="Đang tải thông báo">
            {[0, 1, 2].map(index => <div key={index} className="space-y-3 px-4 py-4" aria-hidden="true">
              <div className="h-3 w-2/3 animate-pulse rounded bg-foreground/5 motion-reduce:animate-none" />
              <div className="h-3 w-full animate-pulse rounded bg-foreground/5 motion-reduce:animate-none" />
              <div className="h-3 w-1/3 animate-pulse rounded bg-foreground/5 motion-reduce:animate-none" />
            </div>)}
          </div> : snapshot?.page.items.length ? <ul className="divide-y divide-border">
            {snapshot.page.items.map(item => <li key={item.id}>
              <button className={`w-full px-4 py-4 text-left hover:bg-background ${!item.readAt ? 'bg-secondary/50' : ''}`}
                disabled={saving} onClick={() => void view(item)}>
                <div className="flex items-start justify-between gap-3">
                  <p className="min-w-0 break-words text-sm font-medium text-pretty">{item.title}</p>
                  {!item.readAt && <span className="shrink-0 text-xs text-muted-foreground">Chưa đọc</span>}
                </div>
                <p className="mt-1 break-words text-sm leading-relaxed text-muted-foreground text-pretty">{item.message}</p>
                <time dateTime={item.createdAt} className="mt-2 block text-xs tabular-nums text-muted-foreground">{date(item.createdAt)}</time>
              </button>
            </li>)}
          </ul> : !error && <p className="px-4 py-8 text-center text-sm text-muted-foreground">Bạn chưa có thông báo nào.</p>}
        </div>
        {snapshot && snapshot.page.totalPage > 1 && <div className="flex items-center justify-between border-t border-border px-4 py-3">
          <span className="text-xs tabular-nums text-muted-foreground">Trang {pageNo} / {snapshot.page.totalPage}</span>
          <div className="flex gap-2">
            <Button size="icon" variant="ghost" aria-label="Thông báo mới hơn" disabled={loading || pageNo <= 1}
              onClick={() => setPageNo(value => value - 1)}><ChevronLeft size={16} /></Button>
            <Button size="icon" variant="ghost" aria-label="Thông báo cũ hơn" disabled={loading || pageNo >= snapshot.page.totalPage}
              onClick={() => setPageNo(value => value + 1)}><ChevronRight size={16} /></Button>
          </div>
        </div>}
      </PopoverContent>
    </Popover>
  );
}
