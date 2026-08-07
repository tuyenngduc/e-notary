import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { DashboardLayout } from '../../components/DashboardLayout';
import { listAcceptedNotaryRequestsApi, listNotaryRequestsApi } from '../../features/requests/requestApi';
import { toApiErrorMessage } from '../../lib/apiError';
import { toContractTypeLabel, toRequestStatusMeta } from '../../lib/enumLabels';
import type { NotaryRequest, RequestStatus } from '../../types/request';

type NotaryTab = 'processing' | 'accepted';

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function getTabFromQuery(value: string | null): NotaryTab {
  return value === 'accepted' ? 'accepted' : 'processing';
}

export function NotaryRequestsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = getTabFromQuery(searchParams.get('tab'));

  const [processingRequests, setProcessingRequests] = useState<NotaryRequest[]>([]);
  const [acceptedRequests, setAcceptedRequests] = useState<NotaryRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<RequestStatus | 'ALL'>('ALL');

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const [processingData, acceptedData] = await Promise.all([
        listNotaryRequestsApi('PROCESSING'),
        listAcceptedNotaryRequestsApi(),
      ]);
      setProcessingRequests(processingData);
      setAcceptedRequests(acceptedData);
    } catch (loadError) {
      setError(toApiErrorMessage(loadError, 'Không tải được danh sách yêu cầu'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const visibleRequests = useMemo(() => {
    const source = tab === 'processing' ? processingRequests : acceptedRequests;

    const keyword = search.trim().toLowerCase();
    const byKeyword = keyword
      ? source.filter((r) => {
          const id = r.requestId.toLowerCase();
          const contractType = (r.contractType ?? '').toLowerCase();
          const description = (r.description ?? '').toLowerCase();
          return id.includes(keyword) || contractType.includes(keyword) || description.includes(keyword);
        })
      : source;

    const byStatus =
      statusFilter === 'ALL'
        ? byKeyword
        : byKeyword.filter((r) => String(r.status) === String(statusFilter));

    return byStatus;
  }, [acceptedRequests, processingRequests, search, statusFilter, tab]);

  const stats = useMemo(() => {
    const scheduled = acceptedRequests.filter((r) => r.status === 'SCHEDULED').length;
    const awaitingPayment = acceptedRequests.filter((r) => r.status === 'AWAITING_PAYMENT').length;
    const completed = acceptedRequests.filter((r) => r.status === 'COMPLETED').length;
    return {
      pending: processingRequests.length,
      accepted: acceptedRequests.length,
      scheduled,
      awaitingPayment,
      completed,
    };
  }, [acceptedRequests, processingRequests.length]);

  const setTab = (next: NotaryTab) => {
    setSearchParams((prev) => {
      prev.set('tab', next);
      return prev;
    });
  };

  return (
    <DashboardLayout role="notary">
      <div className="page-content">
        <div className="page-header with-action">
          <div>
            <h1>Yêu cầu công chứng</h1>
            <p>Quản lý hàng đợi tiếp nhận và các hồ sơ bạn đang xử lý.</p>
          </div>
          <button type="button" className="link-btn" onClick={() => void load()} disabled={loading}>
            Làm mới
          </button>
        </div>

        <section className="stats-grid" style={{ marginBottom: '1.5rem' }}>
          <article className="soft-card stat-card">
            <p>Chờ tiếp nhận</p>
            <h3>{stats.pending}</h3>
          </article>
          <article className="soft-card stat-card">
            <p>Đang xử lý</p>
            <h3>{stats.accepted}</h3>
          </article>
          <article className="soft-card stat-card">
            <p>Đã lên lịch</p>
            <h3>{stats.scheduled}</h3>
          </article>
          <article className="soft-card stat-card">
            <p>Chờ thanh toán</p>
            <h3>{stats.awaitingPayment}</h3>
          </article>
        </section>

        <div className="role-tabs" style={{ marginBottom: '1rem' }}>
          <button
            type="button"
            className={`tab-btn ${tab === 'processing' ? 'tab-btn-active' : ''}`}
            onClick={() => setTab('processing')}
          >
            Đang chờ tiếp nhận
          </button>
          <button
            type="button"
            className={`tab-btn ${tab === 'accepted' ? 'tab-btn-active' : ''}`}
            onClick={() => setTab('accepted')}
          >
            Hồ sơ của tôi
          </button>
        </div>

        <section className="soft-card list-card">
          <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', flexWrap: 'wrap', marginBottom: '1rem' }}>
            <div className="field" style={{ flex: 1, minWidth: 240 }}>
              <span>Tìm kiếm</span>
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Mã yêu cầu / loại hợp đồng / mô tả"
              />
            </div>
            <div className="field" style={{ width: 240 }}>
              <span>Trạng thái</span>
              <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as any)}>
                <option value="ALL">Tất cả</option>
                {tab === 'processing' ? <option value="PROCESSING">Chờ tiếp nhận</option> : null}
                {tab === 'accepted' ? (
                  <>
                    <option value="ACCEPTED">Đã tiếp nhận</option>
                    <option value="SCHEDULED">Đã lên lịch</option>
                    <option value="AWAITING_PAYMENT">Chờ thanh toán</option>
                    <option value="COMPLETED">Hoàn thành</option>
                    <option value="REJECTED">Bị từ chối</option>
                    <option value="CANCELLED">Đã hủy</option>
                  </>
                ) : null}
              </select>
            </div>
          </div>

          {loading ? <p className="muted-text">Đang tải dữ liệu...</p> : null}
          {error ? <div className="form-error">{error}</div> : null}

          {!loading && !error && visibleRequests.length === 0 ? (
            <p className="muted-text">Không có yêu cầu phù hợp.</p>
          ) : null}

          {!loading && !error && visibleRequests.length > 0 ? (
            <div className="list-stack">
              {visibleRequests.map((request) => {
                const statusInfo = toRequestStatusMeta(request.status);

                return (
                  <Link
                    key={request.requestId}
                    className="list-row"
                    to={`/notary/request/${request.requestId}`}
                  >
                    <div>
                      <h3>{toContractTypeLabel(request.contractType)}</h3>
                      <p className="muted-text">Tạo: {formatDate(request.createdAt)}</p>
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

