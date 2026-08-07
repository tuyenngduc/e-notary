import { api } from '../../lib/http';
import type { NotificationItem, UnreadNotificationCount } from '../../types/notification';

interface ApiEnvelope<T> {
  status: number;
  message: string;
  data: T;
}

export const NOTIFICATIONS_UPDATED_EVENT = 'enotary-notifications-updated';

function dispatchNotificationsUpdated() {
  window.dispatchEvent(new Event(NOTIFICATIONS_UPDATED_EVENT));
}

export async function listMyNotificationsApi(): Promise<NotificationItem[]> {
  const response = await api.get<ApiEnvelope<NotificationItem[]>>('/api/notifications/me');
  return response.data.data;
}

export async function getUnreadNotificationCountApi(): Promise<number> {
  const response = await api.get<ApiEnvelope<UnreadNotificationCount>>('/api/notifications/unread-count');
  return response.data.data.count;
}

export async function markNotificationReadApi(id: string): Promise<NotificationItem> {
  const response = await api.put<ApiEnvelope<NotificationItem>>(`/api/notifications/${id}/read`);
  dispatchNotificationsUpdated();
  return response.data.data;
}

export async function markAllNotificationsReadApi(): Promise<void> {
  await api.put('/api/notifications/read-all');
  dispatchNotificationsUpdated();
}
