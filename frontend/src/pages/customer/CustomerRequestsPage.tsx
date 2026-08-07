import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { DashboardLayout } from '../../components/DashboardLayout';
import { listMyRequestsApi } from '../../features/requests/requestApi';
import { toApiErrorMessage } from '../../lib/apiError';
import type { NotaryRequest, RequestStatus } from '../../types/request';

const statusMeta: Record<string, { label: string; tone: string }> = {
  NEW: { label: 'Mới tạo', tone: 'badge-gray' },
  PROCESSING: { label: 'Đang xử lý', tone: 'badge-blue' },
  ACCEPTED: { label: 'Đã tiếp nhận', tone: 'badge-indigo' },
  SCHEDULED: { label: 'Đã lên lịch', tone: 'badge-purple' },
  IN_VIDEO_CALL: { label: 'Đang xác thực danh tính (Video)', tone: 'badge-orange' },
  AWAITING_PAYMENT: { label: 'Chờ thanh toán', tone: 'badge-yellow' },
  COMPLETED: { label: 'Hoàn thành', tone: 'badge-green' },
  CANCELLED: { label: 'Đã hủy', tone: 'badge-gray' },
  REJECTED: { label: 'Bị từ chối', tone: 'badge-red' },
};

const contractTypeLabels: Record<string, string> = {
  TRANSFER_OF_PROPERTY: 'Hợp đồng chuyển nhượng',
  POWER_OF_ATTORNEY: 'Giấy ủy quyền',
  PERSONAL_COMMITMENT: 'Văn bản cam kết cá nhân',
  SIGNATURE_CERTIFICATION: 'Xác nhận chữ ký',
  E_COPY_CERTIFICATION: 'Chứng thực bản sao',
  LOAN_AGREEMENT: 'Hợp đồng vay mượn',
  WILL: 'Di chúc',
  CIVIL_AGREEMENT: 'Thỏa thuận dân sự',
  MARRIAGE_CONTRACT: 'Hợp đồng hôn nhân',
  BUSINESS_CONTRACT: 'Hợp đồng thương mại',
  OTHER: 'Loại khác',
};

const serviceTypeLabels: Record<string, string> = {
  ONLINE: 'Trực tuyến',
  OFFLINE: 'Trực tiếp',
};

function toStatus(status: RequestStatus) {
  return statusMeta[status] ?? { label: status, tone: 'badge-gray' };
}

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

export function CustomerRequestsPage() {
  const [requests, setRequests] = useState<NotaryRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const data = await listMyRequestsApi();
        setRequests(data);
      } catch (loadError) {
        setError(toApiErrorMessage(loadError, 'Không tải được danh sách yêu cầu'));
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, []);

  return (
    <DashboardLayout role="customer">
      <div className="page-content">
        <div className="page-header with-action">
          <div>
            <h1>Yêu cầu công chứng</h1>
            <p>Quản lý toàn bộ yêu cầu công chứng của bạn.</p>
          </div>
          <Link className="primary-btn" to="/customer/new-request">
            Tạo yêu cầu công chứng
          </Link>
        </div>

        <section className="soft-card list-card">
          {loading ? <p className="muted-text">Đang tải dữ liệu...</p> : null}
          {error ? <div className="form-error">{error}</div> : null}

          {!loading && !error && requests.length === 0 ? (
            <div className="empty-state">
              <p>Bạn chưa có yêu cầu công chứng nào.</p>
              <Link className="ghost-btn" to="/customer/new-request" style={{ marginTop: '1rem' }}>
                Tạo yêu cầu đầu tiên
              </Link>
            </div>
          ) : null}

          {!loading && !error && requests.length > 0 ? (
            <div className="list-stack">
              {requests.map((request) => {
                const statusInfo = toStatus(request.status);
                return (
                  <Link key={request.requestId} className="list-row" to={`/customer/request/${request.requestId}`}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                      <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: 'rgba(79, 70, 229, 0.1)', color: 'var(--primary)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                      </div>
                      <div>
                        <h3 style={{ fontSize: '1.05rem', marginBottom: '0.2rem' }}>{contractTypeLabels[request.contractType] || request.contractType}</h3>
                        <p className="muted-text" style={{ fontSize: '0.85rem' }}>
                          Dịch vụ: {serviceTypeLabels[request.serviceType] || request.serviceType} - Tạo lúc: {formatDate(request.createdAt)}
                        </p>
                      </div>
                    </div>
                    <span className={`status-badge ${statusInfo.tone}`}>{statusInfo.label}</span>
                  </Link>
                );
              })}
            </div>
          ) : null}
        </section>
      </div>
    </DashboardLayout>
  );
}
