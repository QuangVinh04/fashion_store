import { api, json, type Page } from './client';

export type StoreNotification = {
  id: string; type: string; title: string; message: string; orderId: string;
  createdAt: string; readAt: string | null;
};
export type NotificationSnapshot = { page: Page<StoreNotification>; unreadCount: number };

export const notifications = {
  list: (pageNo = 1, signal?: AbortSignal) => api<NotificationSnapshot>(
    `/api/v1/notifications?pageNo=${pageNo}&pageSize=10`, { signal }),
  read: (id: string) => api<void>(`/api/v1/notifications/${encodeURIComponent(id)}/read`, json('PATCH')),
  readAll: () => api<void>('/api/v1/notifications/read-all', json('PATCH')),
};

export const notificationLink = (orderId: string) => `/profile/orders/${encodeURIComponent(orderId)}`;
