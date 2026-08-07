export interface NotificationItem {
  id: string;
  title: string;
  message: string;
  type: string;
  requestId?: string | null;
  appointmentId?: string | null;
  isRead: boolean;
  createdAt: string;
  readAt?: string | null;
}

export interface UnreadNotificationCount {
  count: number;
}
