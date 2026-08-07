import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DashboardLayout } from '../../components/DashboardLayout';
import { listMyAppointmentsApi, verifyVideoTokenApi } from '../../features/requests/requestApi';
import { toApiErrorMessage } from '../../lib/apiError';
import { toContractTypeLabel, toServiceTypeLabel } from '../../lib/enumLabels';
import { getVideoRoomPathFromMeetingUrl } from '../../lib/videoRoom';
import type { Appointment } from '../../types/request';

function formatDateObj(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return { date: value, time: '' };

  return {
    date: date.toLocaleDateString('vi-VN', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }),
    time: date.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
  };
}

function getVideoTokenFromMeetingUrl(meetingUrl: string | null | undefined) {
  if (!meetingUrl || !meetingUrl.trim()) return '';

  try {
    const url = new URL(meetingUrl, window.location.origin);
    return url.searchParams.get('token') || '';
  } catch {
    return '';
  }
}

export function NotaryAppointmentsPage() {
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [openingAppointmentId, setOpeningAppointmentId] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchAppointments = async () => {
      setLoading(true);
      setError('');
      try {
        const data = await listMyAppointmentsApi();
        setAppointments(data);
      } catch (err) {
        setError(toApiErrorMessage(err, 'Không thể tải danh sách lịch hẹn'));
      } finally {
        setLoading(false);
      }
    };

    void fetchAppointments();
  }, []);

  const handleOpenVideoRoom = async (appt: Appointment, videoRoomPath: string) => {
    if (appt.status === 'FINISHED' || appt.status === 'CANCELLED') {
      setError('Lịch hẹn này đã kết thúc hoặc đã hủy, không thể mở lại phòng họp.');
      return;
    }

    const token = getVideoTokenFromMeetingUrl(appt.meetingUrl);
    if (!token) {
      setError('Thiếu token truy cập phòng họp. Vui lòng mở phòng từ lịch hẹn hợp lệ.');
      return;
    }

    setOpeningAppointmentId(appt.appointmentId);
    setError('');
    try {
      const session = await verifyVideoTokenApi(token);
      if (session.status === 'FINISHED' || session.status === 'CANCELLED' || session.endedAt) {
        setError('Phiên video đã kết thúc hoặc đã hủy, không thể mở lại phòng họp.');
        return;
      }
      navigate(videoRoomPath);
    } catch (err) {
      setError(toApiErrorMessage(err, 'Không thể kiểm tra trạng thái phòng họp.'));
    } finally {
      setOpeningAppointmentId(null);
    }
  };

  return (
    <DashboardLayout role="notary">
      <div className="page-content">
        <div className="page-header">
          <h1>Lịch hẹn của tôi</h1>
          <p>Danh sách các cuộc hẹn công chứng đã được lên lịch</p>
        </div>

        {error ? <div className="form-error" style={{ marginBottom: '1.5rem' }}>{error}</div> : null}

        {loading ? (
          <p className="muted-text">Đang tải lịch hẹn...</p>
        ) : appointments.length === 0 ? (
          <div className="soft-card" style={{ textAlign: 'center', padding: '4rem 2rem' }}>
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" style={{ color: '#94a3b8', margin: '0 auto 1rem' }}>
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect>
              <line x1="16" y1="2" x2="16" y2="6"></line>
              <line x1="8" y1="2" x2="8" y2="6"></line>
              <line x1="3" y1="10" x2="21" y2="10"></line>
            </svg>
            <h3 style={{ marginBottom: '0.5rem' }}>Chưa có lịch hẹn nào</h3>
            <p className="muted-text">Bạn hiện không có lịch hẹn công chứng nào sắp tới.</p>
          </div>
        ) : (
          <div className="list-stack">
            {appointments.map((appt) => {
              const { date, time } = formatDateObj(appt.scheduledTime);
              const isOnline = appt.serviceType === 'ONLINE';
              const videoRoomPath = getVideoRoomPathFromMeetingUrl(appt.meetingUrl);
              const isClosedAppointment = appt.status === 'FINISHED' || appt.status === 'CANCELLED';

              return (
                <div className="list-row" key={appt.appointmentId} style={{ alignItems: 'flex-start' }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
                      <span style={{ fontSize: '1.1rem', fontWeight: 700, color: '#1e293b' }}>{time}</span>
                      <span style={{ color: '#64748b', fontSize: '0.9rem' }}>|</span>
                      <span style={{ color: '#475569', fontWeight: 500 }}>{date}</span>
                      <span className={`status-badge ${isOnline ? 'badge-blue' : 'badge-green'}`} style={{ marginLeft: '0.5rem' }}>
                        {toServiceTypeLabel(appt.serviceType)}
                      </span>
                    </div>

                    <h3 style={{ fontSize: '1.15rem', marginBottom: '0.25rem' }}>
                      Khách hàng: {appt.clientName || 'Khách Vãng Lai'}
                    </h3>
                    <p style={{ color: '#475569', marginBottom: '0.75rem' }}>
                      Loại hợp đồng: <b>{toContractTypeLabel(appt.contractType)}</b>
                    </p>

                    {!isOnline ? (
                      <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.5rem', fontSize: '0.9rem', color: '#64748b' }}>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ marginTop: '2px' }}>
                          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                          <circle cx="12" cy="10" r="3"></circle>
                        </svg>
                        <span>{appt.physicalAddress || 'Văn phòng công chứng'}</span>
                      </div>
                    ) : null}
                  </div>

                  <div className="doc-actions" style={{ flexDirection: 'column', gap: '0.75rem', minWidth: '160px' }}>
                    {isOnline && videoRoomPath ? (
                      isClosedAppointment ? (
                        <span className="status-badge badge-gray" style={{ textAlign: 'center' }}>
                          Phòng đã đóng
                        </span>
                      ) : (
                        <button
                          type="button"
                          className="primary-btn"
                          style={{ justifyContent: 'center' }}
                          disabled={openingAppointmentId === appt.appointmentId}
                          onClick={() => void handleOpenVideoRoom(appt, videoRoomPath)}
                        >
                          {openingAppointmentId === appt.appointmentId ? 'Đang kiểm tra...' : 'Mở phòng họp'}
                        </button>
                      )
                    ) : null}

                    <button
                      type="button"
                      className={isOnline ? "link-btn" : "primary-btn"}
                      onClick={() => navigate(`/notary/request/${appt.requestId}`)}
                      style={{ justifyContent: 'center' }}
                    >
                      Xem yêu cầu
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </DashboardLayout>
  );
}

