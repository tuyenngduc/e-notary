import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DashboardLayout } from '../../components/DashboardLayout';
import {
  listMyNotificationsApi,
  markAllNotificationsReadApi,
  markNotificationReadApi,
} from '../../features/notifications/notificationApi';
import { toApiErrorMessage } from '../../lib/apiError';
import type { NotificationItem } from '../../types/notification';

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}

export function CustomerNotificationsPage() {
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const unreadCount = notifications.filter((notification) => !notification.isRead).length;

  const loadNotifications = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await listMyNotificationsApi();
      setNotifications(data);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError, 'Không tải được danh sách thông báo'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadNotifications();
  }, []);

  const handleOpenNotification = async (notification: NotificationItem) => {
    if (!notification.isRead) {
      try {
        const updated = await markNotificationReadApi(notification.id);
        setNotifications((current) =>
          current.map((item) => (item.id === notification.id ? updated : item)),
        );
      } catch {
        setNotifications((current) =>
          current.map((item) => (item.id === notification.id ? { ...item, isRead: true } : item)),
        );
      }
    }

    if (notification.requestId) {
      navigate(`/customer/request/${notification.requestId}`);
    }
  };

  const handleMarkAllRead = async () => {
    try {
      await markAllNotificationsReadApi();
      setNotifications((current) =>
        current.map((notification) => ({ ...notification, isRead: true, readAt: notification.readAt ?? new Date().toISOString() })),
      );
    } catch (markError) {
      setError(toApiErrorMessage(markError, 'Không thể đánh dấu đã đọc'));
    }
  };

  return (
    <DashboardLayout role="customer">
      <div className="page-content">
        <div className="page-header with-action">
          <div>
            <h1>Thông báo</h1>
            <p>Theo dõi lịch hẹn và các cập nhật mới nhất của hồ sơ công chứng.</p>
          </div>
          {unreadCount > 0 ? (
            <button type="button" className="ghost-btn" onClick={handleMarkAllRead}>
              Đánh dấu tất cả đã đọc
            </button>
          ) : null}
        </div>

        <section className="notifications-panel">
          {loading ? <p className="muted-text">Đang tải thông báo...</p> : null}
          {error ? <div className="form-error">{error}</div> : null}

          {!loading && !error && notifications.length === 0 ? (
            <div className="empty-state">
              <p>Bạn chưa có thông báo nào.</p>
            </div>
          ) : null}

          {!loading && notifications.length > 0 ? (
            <div className="notification-list">
              {notifications.map((notification) => (
                <button
                  key={notification.id}
                  type="button"
                  className={`notification-card ${notification.isRead ? '' : 'unread'}`}
                  onClick={() => void handleOpenNotification(notification)}
                >
                  <div className="notification-icon" aria-hidden="true">
                    <BellSmallIcon />
                  </div>
                  <div className="notification-content">
                    <div className="notification-title-row">
                      <h3>{notification.title}</h3>
                      {!notification.isRead ? <span className="notification-new-badge">Mới</span> : null}
                    </div>
                    <p>{notification.message}</p>
                    <span>{formatDateTime(notification.createdAt)}</span>
                  </div>
                </button>
              ))}
            </div>
          ) : null}
        </section>
      </div>
    </DashboardLayout>
  );
}

function BellSmallIcon() {
  return (
    <svg viewBox="0 0 24 24">
      <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"></path>
      <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
    </svg>
  );
}
