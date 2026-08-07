
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { DashboardLayout } from '../../components/DashboardLayout';
import { listMyRequestsApi } from '../../features/requests/requestApi';
import { toApiErrorMessage } from '../../lib/apiError';
import type { NotaryRequest } from '../../types/request';

export function CustomerDashboardPage() {
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

  const stats = useMemo(() => {
    const completed = requests.filter((r) => r.status === 'COMPLETED').length;
    const inProgress = requests.filter((r) => ['PROCESSING', 'ACCEPTED', 'SCHEDULED'].includes(r.status)).length;
    const waitingPayment = requests.filter((r) => r.status === 'AWAITING_PAYMENT').length;

    return {
      total: requests.length,
      completed,
      inProgress,
      waitingPayment,
    };
  }, [requests]);

  return (
    <DashboardLayout role="customer">
      <div className="page-content">
        <div className="page-header with-action">
          <div>
            <h1>Trang chủ</h1>
            <p>Quản lý toàn bộ yêu cầu công chứng của bạn.</p>
          </div>
          <Link className="primary-btn" to="/customer/requests">
            Yêu cầu công chứng
          </Link>
        </div>

        {loading ? <p className="muted-text">Đang tải dữ liệu...</p> : null}
        {error ? <div className="form-error">{error}</div> : null}

        <section className="soft-card" style={{ marginBottom: '3rem', background: 'var(--bg-card)', border: '1px solid rgba(79, 70, 229, 0.1)', position: 'relative', overflow: 'hidden' }}>
          <div style={{ position: 'absolute', top: '-50px', right: '-50px', width: '200px', height: '200px', background: 'radial-gradient(circle, rgba(79,70,229,0.08) 0%, transparent 70%)', borderRadius: '50%' }}></div>
          <h2 style={{ textAlign: 'center', marginBottom: '2.5rem', fontSize: '1.75rem', fontWeight: 700, color: 'var(--secondary)' }}>Quy trình thực hiện</h2>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '2rem', textAlign: 'center', position: 'relative' }}>

            <div style={{ position: 'relative', zIndex: 1 }}>
              <div style={{ width: '64px', height: '64px', borderRadius: '20px', background: 'linear-gradient(135deg, var(--primary) 0%, #8b5cf6 100%)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem', boxShadow: '0 10px 25px rgba(79,70,229,0.3)' }}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><circle cx="9" cy="10" r="2"></circle><path d="M15 13h2"></path><path d="M15 17h2"></path><path d="M5 18l2-2h4l2 2"></path></svg>
              </div>
              <h4 style={{ marginBottom: '0.5rem', fontSize: '1.1rem', fontWeight: 700 }}>1. Xác thực CCCD</h4>
              <p className="muted-text" style={{ fontSize: '0.9rem', lineHeight: 1.5 }}>Hệ thống tự động trích xuất & xác minh danh tính hợp pháp từ CCCD gắn chip của bạn.</p>
            </div>

            <div style={{ position: 'relative', zIndex: 1 }}>
              <div style={{ width: '64px', height: '64px', borderRadius: '20px', background: 'linear-gradient(135deg, #10b981 0%, #059669 100%)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem', boxShadow: '0 10px 25px rgba(16,185,129,0.3)' }}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="12" y1="18" x2="12" y2="12"></line><line x1="9" y1="15" x2="15" y2="15"></line></svg>
              </div>
              <h4 style={{ marginBottom: '0.5rem', fontSize: '1.1rem', fontWeight: 700 }}>2. Tạo yêu cầu</h4>
              <p className="muted-text" style={{ fontSize: '0.9rem', lineHeight: 1.5 }}>Chọn loại dịch vụ (ủy quyền, mua bán...) và điền các thông tin cơ bản liên quan đến hồ sơ.</p>
            </div>

            <div style={{ position: 'relative', zIndex: 1 }}>
              <div style={{ width: '64px', height: '64px', borderRadius: '20px', background: 'linear-gradient(135deg, #f59e0b 0%, #d97706 100%)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem', boxShadow: '0 10px 25px rgba(245,158,11,0.3)' }}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
              </div>
              <h4 style={{ marginBottom: '0.5rem', fontSize: '1.1rem', fontWeight: 700 }}>3. Đẩy tài liệu lên</h4>
              <p className="muted-text" style={{ fontSize: '0.9rem', lineHeight: 1.5 }}>Chụp ảnh hoặc upload các giấy tờ, bản dự thảo hợp đồng lên hệ thống.</p>
            </div>

            <div style={{ position: 'relative', zIndex: 1 }}>
              <div style={{ width: '64px', height: '64px', borderRadius: '20px', background: 'linear-gradient(135deg, #0ea5e9 0%, #0284c7 100%)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem', boxShadow: '0 10px 25px rgba(14,165,233,0.3)' }}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
              </div>
              <h4 style={{ marginBottom: '0.5rem', fontSize: '1.1rem', fontWeight: 700 }}>4. Chờ lịch hẹn</h4>
              <p className="muted-text" style={{ fontSize: '0.9rem', lineHeight: 1.5 }}>Công chứng viên sẽ tiếp nhận hồ sơ, kiểm tra và thông báo lịch hẹn.</p>
            </div>

            <div style={{ position: 'relative', zIndex: 1 }}>
              <div style={{ width: '64px', height: '64px', borderRadius: '20px', background: 'linear-gradient(135deg, #ec4899 0%, #be185d 100%)', color: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.25rem', boxShadow: '0 10px 25px rgba(236,72,153,0.3)' }}>
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="1" y="4" width="22" height="16" rx="2" ry="2"></rect><line x1="1" y1="10" x2="23" y2="10"></line></svg>
              </div>
              <h4 style={{ marginBottom: '0.5rem', fontSize: '1.1rem', fontWeight: 700 }}>5. Hoàn tất thủ tục</h4>
              <p className="muted-text" style={{ fontSize: '0.9rem', lineHeight: 1.5 }}>Thanh toán phí công chứng an toàn qua thẻ/QR code. Nhận kết quả bản mềm và bản cứng tận nhà.</p>
            </div>

          </div>
        </section>

        <section className="stats-grid">
          <article className="soft-card stat-card">
            <p>Tổng yêu cầu</p>
            <h3>{stats.total}</h3>
          </article>
          <article className="soft-card stat-card">
            <p>Hoàn thành</p>
            <h3>{stats.completed}</h3>
          </article>
          <article className="soft-card stat-card">
            <p>Đang xử lý</p>
            <h3>{stats.inProgress}</h3>
          </article>
          <article className="soft-card stat-card">
            <p>Chờ thanh toán</p>
            <h3>{stats.waitingPayment}</h3>
          </article>
        </section>

      </div>
    </DashboardLayout>
  );
}

