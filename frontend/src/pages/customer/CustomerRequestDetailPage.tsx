import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { DashboardLayout } from '../../components/DashboardLayout';
import {
  downloadDocumentApi,
  getRequestApi,
  getRequestDocumentsApi,
  uploadRequestDocumentApi,
  replaceDocumentApi,
  cancelRequestApi,
} from '../../features/requests/requestApi';
import { confirmPaymentTransferApi, getPaymentByRequestApi } from '../../features/payments/paymentApi';
import { toApiErrorMessage } from '../../lib/apiError';
import { toDocTypeLabel } from '../../lib/enumLabels';
import { getVideoRoomPathFromMeetingUrl } from '../../lib/videoRoom';
import type { PaymentItem } from '../../types/payment';
import type { DocType, DocumentItem, NotaryRequest } from '../../types/request';

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

const SIGNED_DOCUMENT_TYPE = 'SIGNED_DOCUMENT';

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function getStoredFileName(filePath: string) {
  return filePath.split('/').pop() || filePath;
}

function getDocumentFileName(item: DocumentItem) {
  return item.displayName || item.originalFileName || getStoredFileName(item.filePath) || `${item.documentId}.bin`;
}

export function CustomerRequestDetailPage() {
  const { id = '' } = useParams();
  const [request, setRequest] = useState<NotaryRequest | null>(null);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [uploading, setUploading] = useState(false);
  const [viewingDocUrl, setViewingDocUrl] = useState<string | null>(null);
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);
  const [payment, setPayment] = useState<PaymentItem | null>(null);
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [paymentSubmitting, setPaymentSubmitting] = useState(false);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const [requestData, docsData] = await Promise.all([
          getRequestApi(id),
          getRequestDocumentsApi(id),
        ]);
        setRequest(requestData);
        setDocuments(docsData);
      } catch (loadError) {
        setError(toApiErrorMessage(loadError, 'Không tải được thông tin yêu cầu'));
      } finally {
        setLoading(false);
      }
    };

    if (id) {
      void load();
    }
  }, [id]);

  useEffect(() => {
    if (!request || request.status !== 'AWAITING_PAYMENT') {
      setPayment(null);
      return;
    }

    const loadPayment = async () => {
      setPaymentLoading(true);
      try {
        const data = await getPaymentByRequestApi(request.requestId);
        setPayment(data);
      } catch (paymentError) {
        setError(toApiErrorMessage(paymentError, 'Không tải được thông tin thanh toán'));
      } finally {
        setPaymentLoading(false);
      }
    };

    void loadPayment();
  }, [request]);

  const statusInfo = useMemo(() => {
    if (!request) return null;
    return statusMeta[request.status] ?? { label: request.status, tone: 'badge-gray' };
  }, [request]);

  const videoRoomPath = useMemo(
    () => request?.serviceType === 'ONLINE' ? getVideoRoomPathFromMeetingUrl(request?.meetingUrl) : null,
    [request?.meetingUrl, request?.serviceType],
  );
  const canJoinVideoSession = !!request
    && request.serviceType === 'ONLINE'
    && (request.status === 'SCHEDULED' || request.status === 'IN_VIDEO_CALL')
    && request.appointment?.status === 'PENDING'
    && !!videoRoomPath;

  const handleDownload = async (item: DocumentItem) => {
    try {
      const blob = await downloadDocumentApi(item.documentId);
      const objectUrl = URL.createObjectURL(blob);
      const anchor = window.document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = getDocumentFileName(item);
      window.document.body.append(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(objectUrl);
    } catch (downloadError) {
      setError(toApiErrorMessage(downloadError, 'Không tải được tài liệu'));
    }
  };

  const handleView = async (item: DocumentItem) => {
    try {
      const blob = await downloadDocumentApi(item.documentId);
      let finalBlob = blob;
      if (item.filePath.toLowerCase().endsWith('.pdf') && blob.type !== 'application/pdf') {
        finalBlob = new Blob([blob], { type: 'application/pdf' });
      } else if ((item.filePath.toLowerCase().endsWith('.jpg') || item.filePath.toLowerCase().endsWith('.jpeg')) && blob.type !== 'image/jpeg') {
        finalBlob = new Blob([blob], { type: 'image/jpeg' });
      } else if (item.filePath.toLowerCase().endsWith('.png') && blob.type !== 'image/png') {
        finalBlob = new Blob([blob], { type: 'image/png' });
      }
      
      const objectUrl = URL.createObjectURL(finalBlob);
      setViewingDocUrl(objectUrl);
    } catch (err) {
      setError(toApiErrorMessage(err, 'Không xem được tài liệu'));
    }
  };

  const handleUploadForType = async (file: File, docType: DocType) => {
    if (!id) return;
    setUploading(true);
    setError('');
    try {
      const doc = await uploadRequestDocumentApi(id, file, docType);
      setDocuments(prev => [...prev, doc]);
    } catch (err) {
      setError(toApiErrorMessage(err, `Lỗi tải lên ${toDocTypeLabel(docType)}`));
    } finally {
      setUploading(false);
    }
  };

  const handleReplace = async (file: File, documentId: string) => {
    setUploading(true);
    setError('');
    try {
      const updatedDoc = await replaceDocumentApi(documentId, file);
      setDocuments(prev => prev.map(d => d.documentId === documentId ? updatedDoc : d));
    } catch (err) {
      setError(toApiErrorMessage(err, 'Lỗi khi thay thế tài liệu'));
    } finally {
      setUploading(false);
    }
  };

  const handleCancelClick = () => {
    setShowCancelConfirm(true);
  };

  const executeCancel = async () => {
    if (!id || !request) return;
    
    setLoading(true);
    setError('');
    try {
      const updated = await cancelRequestApi(id);
      setRequest(updated);
    } catch (err) {
      setError(toApiErrorMessage(err, 'Lỗi khi hủy yêu cầu'));
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmTransfer = async () => {
    if (!payment) return;

    setPaymentSubmitting(true);
    setError('');
    try {
      const updatedPayment = await confirmPaymentTransferApi(payment.paymentId);
      setPayment(updatedPayment);
      if (id) {
        const [requestData, docsData] = await Promise.all([
          getRequestApi(id),
          getRequestDocumentsApi(id),
        ]);
        setRequest(requestData);
        setDocuments(docsData);
      }
    } catch (paymentError) {
      setError(toApiErrorMessage(paymentError, 'Không thể xác nhận thanh toán'));
    } finally {
      setPaymentSubmitting(false);
    }
  };

  const isTerminalStatus = request?.status === 'COMPLETED' || request?.status === 'CANCELLED' || request?.status === 'REJECTED';
  const finalSignedDocument = useMemo(
    () => documents
      .filter((documentItem) => documentItem.docType === SIGNED_DOCUMENT_TYPE)
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0] ?? null,
    [documents],
  );
  const supportingDocuments = useMemo(
    () => documents.filter((documentItem) => documentItem.docType !== SIGNED_DOCUMENT_TYPE),
    [documents],
  );
  const requiredDocumentItems = request?.documentRequirements?.requiredDocuments?.length
    ? request.documentRequirements.requiredDocuments
    : (request?.documentRequirements?.requiredDocTypes || []).map((docType) => ({
        code: docType,
        name: toDocTypeLabel(docType),
        source: 'USER_UPLOAD',
        allowedFileGroup: 'DOCUMENT',
        uploaded: request?.documentRequirements?.uploadedDocTypes.includes(docType) ?? false,
        missing: request?.documentRequirements?.missingDocTypes.includes(docType) ?? false,
      }));
  return (
    <DashboardLayout role="customer">
      <div className="page-content">
        {loading ? <p className="muted-text">Đang tải dữ liệu...</p> : null}
        {error ? <div className="form-error">{error}</div> : null}

        {!loading && request ? (
          <>
            <div className="page-header with-action">
              <div>
                <h1>Chi tiết yêu cầu</h1>
                <p>
                  {contractTypeLabels[request.contractType] || request.contractType} - {serviceTypeLabels[request.serviceType] || request.serviceType}
                </p>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                {statusInfo ? <span className={`status-badge ${statusInfo.tone}`}>{statusInfo.label}</span> : null}
                {!isTerminalStatus && (
                  <button type="button" className="ghost-btn" style={{ color: '#ef4444' }} onClick={handleCancelClick}>
                    Hủy yêu cầu
                  </button>
                )}
              </div>
            </div>

            <div className="content-grid">
              <div className="grid-left" style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', gridColumn: '1 / -1' }}>
                <section className="soft-card">
                  <h2>Thông tin chung</h2>
                  <div className="list-stack">
                    <div className="list-row">
                      <div><p className="muted-text">Mã yêu cầu</p><h4>{request.requestId.split('-')[0]}</h4></div>
                    </div>
                    <div className="list-row">
                      <div><p className="muted-text">Ngày tạo</p><h4>{formatDate(request.createdAt)}</h4></div>
                    </div>
                    <div className="list-row">
                      <div><p className="muted-text">Trạng thái</p><h4 style={{ color: 'var(--primary)' }}>{statusInfo?.label}</h4></div>
                    </div>
                    <div className="list-row">
                      <div><p className="muted-text">Mô tả</p><h4>{request.description || 'Không có mô tả'}</h4></div>
                    </div>
                  </div>

                  {request.rejectionReason ? (
                    <div className="inline-warning" style={{ marginTop: '1rem' }}>Lý do từ chối: {request.rejectionReason}</div>
                  ) : null}

                  {canJoinVideoSession ? (
                    <div style={{ marginTop: '1rem' }}>
                      <Link className="primary-btn inline-btn" to={videoRoomPath!}>
                        Tham gia phiên công chứng
                      </Link>
                    </div>
                  ) : null}
                </section>

                {request.status === 'AWAITING_PAYMENT' ? (
                  <section className="soft-card payment-transfer-card">
                    <div className="section-heading-row">
                      <div>
                        <span className="section-kicker">Thanh toán</span>
                        <h2>Thanh toán phí công chứng</h2>
                      </div>
                      <span className="status-badge badge-yellow">Chờ thanh toán</span>
                    </div>

                    {paymentLoading ? <p className="muted-text">Đang tải thông tin thanh toán...</p> : null}
                    {!paymentLoading && payment ? (
                      <div className="payment-transfer-grid">
                        <div className="payment-transfer-info">
                          <div className="payment-amount">
                            {Number(payment.amount).toLocaleString('vi-VN')} VND
                          </div>
                          <div className="list-stack">
                            <div className="list-row">
                              <div><p className="muted-text">Ngân hàng</p><h4>{payment.bankCode}</h4></div>
                            </div>
                            <div className="list-row">
                              <div><p className="muted-text">Số tài khoản</p><h4>{payment.accountNumber}</h4></div>
                            </div>
                            <div className="list-row">
                              <div><p className="muted-text">Chủ tài khoản</p><h4>{payment.accountName}</h4></div>
                            </div>
                            <div className="list-row">
                              <div><p className="muted-text">Nội dung chuyển khoản</p><h4>{payment.transferContent}</h4></div>
                            </div>
                          </div>
                        </div>

                        <div className="payment-qr-panel">
                          <img src={payment.qrImageUrl} alt="QR chuyển khoản phí công chứng" />
                          <p className="muted-text">Quét mã QR hoặc chuyển khoản đúng nội dung ở trên.</p>
                          <button
                            type="button"
                            className="primary-btn w-full"
                            onClick={handleConfirmTransfer}
                            disabled={paymentSubmitting || payment.paymentStatus === 'SUCCESS'}
                          >
                            {paymentSubmitting
                              ? 'Đang xác nhận...'
                              : payment.paymentStatus === 'SUCCESS'
                                ? 'Đã thanh toán'
                                : 'Tôi đã chuyển khoản'}
                          </button>
                        </div>
                      </div>
                    ) : null}
                  </section>
                ) : null}

                {request.status === 'COMPLETED' && finalSignedDocument ? (
                  <section className="final-notarized-document">
                    <div className="final-notarized-summary">
                      <span className="section-kicker">Văn bản cuối cùng</span>
                      <h2>Văn bản công chứng</h2>
                      <p>
                        Đây là bản công chứng hoàn chỉnh sau khi thanh toán thành công.
                        Vui lòng sử dụng bản này cho mục đích tra cứu, lưu trữ hoặc đối chiếu pháp lý.
                      </p>
                    </div>

                    <div className="final-notarized-file">
                      <div className="final-notarized-icon" aria-hidden="true">
                        <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                          <polyline points="14 2 14 8 20 8" />
                          <path d="m9 15 2 2 4-5" />
                        </svg>
                      </div>
                      <div className="final-notarized-meta">
                        <strong>{getDocumentFileName(finalSignedDocument)}</strong>
                        <span>Hoàn tất lúc {formatDate(finalSignedDocument.createdAt)}</span>
                      </div>
                      <div className="final-notarized-actions">
                        <button type="button" className="primary-btn" onClick={() => handleView(finalSignedDocument)}>
                          Xem văn bản
                        </button>
                        <button type="button" className="ghost-btn" onClick={() => handleDownload(finalSignedDocument)}>
                          Tải về
                        </button>
                      </div>
                    </div>
                  </section>
                ) : null}

                {request.appointment ? (
                  <section className="soft-card">
                    <h2>Lịch hẹn</h2>
                    <div className="list-stack">
                      <div className="list-row">
                        <div><p className="muted-text">Thời gian</p><h4>{formatDate(request.appointment.scheduledTime)}</h4></div>
                      </div>
                      <div className="list-row">
                        <div><p className="muted-text">Công chứng viên</p><h4>{request.appointment.notaryName || 'Công chứng viên phụ trách'}</h4></div>
                      </div>
                      {request.serviceType === 'OFFLINE' ? (
                        <div className="list-row">
                          <div><p className="muted-text">Địa điểm</p><h4>{request.appointment.physicalAddress || 'Văn phòng công chứng'}</h4></div>
                        </div>
                      ) : null}
                    </div>
                  </section>
                ) : null}

                <section>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                    <h2 style={{ margin: 0 }}>Hồ sơ tài liệu</h2>
                    {uploading && <span className="muted-text" style={{ fontSize: '0.9rem' }}>Đang tải lên...</span>}
                  </div>
                  <p className="muted-text" style={{ marginBottom: '1.5rem' }}>Vui lòng bổ sung đầy đủ các giấy tờ còn thiếu để hồ sơ sớm được thẩm định.</p>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '1.5rem' }}>
                    {requiredDocumentItems.map(requiredDoc => {
                      const docType = requiredDoc.code;
                      const docsForType = supportingDocuments.filter(d => d.docType === docType);
                      const isSystemGenerated = requiredDoc.source === 'SYSTEM_GENERATED';
                      return (
                        <div key={docType} className="soft-card" style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '1rem', border: docsForType.length === 0 && !isSystemGenerated ? '1px dashed #cbd5e1' : undefined }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <h3 style={{ fontSize: '1rem', margin: 0 }}>{requiredDoc.name || toDocTypeLabel(docType)}</h3>
                            <span className={`status-badge ${docsForType.length > 0 || isSystemGenerated ? 'badge-green' : 'badge-yellow'}`}>
                              {isSystemGenerated ? 'Hệ thống tạo' : docsForType.length > 0 ? `${docsForType.length} tệp` : 'Còn thiếu'}
                            </span>
                          </div>

                          {isSystemGenerated ? (
                            <div style={{ padding: '1.5rem 0', textAlign: 'center', background: '#f8fafc', borderRadius: '8px' }}>
                              <p className="muted-text" style={{ fontSize: '0.85rem', margin: 0 }}>Tài liệu này được hệ thống ghi nhận tự động.</p>
                            </div>
                          ) : docsForType.length > 0 ? (
                            <div className="list-stack" style={{ gap: '0.5rem' }}>
                              {docsForType.map(item => (
                                <div key={item.documentId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(0,0,0,0.02)', padding: '0.5rem 0.75rem', borderRadius: '6px' }}>
                                  <span style={{ fontSize: '0.85rem', wordBreak: 'break-all', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>
                                    {getDocumentFileName(item)}
                                  </span>
                                  <div style={{ display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
                                    <button type="button" className="ghost-btn" style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem', minWidth: 'auto', color: 'var(--primary)' }} onClick={() => handleView(item)}>Xem</button>
                                    <button type="button" className="ghost-btn" style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem', minWidth: 'auto' }} onClick={() => handleDownload(item)}>Tải về</button>
                                    {!isTerminalStatus && (
                                      <label className="ghost-btn" style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem', minWidth: 'auto', color: '#f59e0b', cursor: uploading ? 'wait' : 'pointer', margin: 0, fontWeight: 500 }}>
                                        Cập nhật
                                        <input type="file" style={{ display: 'none' }} disabled={uploading} onChange={(e) => {
                                          const file = e.target.files?.[0];
                                          if (file) handleReplace(file, item.documentId);
                                          e.target.value = '';
                                        }} />
                                      </label>
                                    )}
                                  </div>
                                </div>
                              ))}
                            </div>
                          ) : (
                            <div style={{ padding: '1.5rem 0', textAlign: 'center', background: '#f8fafc', borderRadius: '8px' }}>
                              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#94a3b8" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginBottom: '0.5rem' }}><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="12" y1="18" x2="12" y2="12"></line><line x1="9" y1="15" x2="15" y2="15"></line></svg>
                              <p className="muted-text" style={{ fontSize: '0.85rem', margin: 0 }}>Chưa có tài liệu</p>
                            </div>
                          )}

                          <div style={{ marginTop: 'auto', textAlign: 'center' }}>
                            {isTerminalStatus ? (
                              <p className="muted-text" style={{ fontSize: '0.8rem', margin: 0 }}>Không thể cập nhật tài liệu</p>
                            ) : isSystemGenerated ? (
                              <p className="muted-text" style={{ fontSize: '0.8rem', margin: 0 }}>Không cần tải lên</p>
                            ) : (
                              <label className="primary-btn ghost-btn" style={{ width: '100%', cursor: uploading ? 'wait' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '0.5rem' }}>
                                <input
                                  type="file"
                                  style={{ display: 'none' }}
                                  disabled={uploading}
                                  onChange={(e) => {
                                    const file = e.target.files?.[0];
                                    if (file) {
                                      handleUploadForType(file, docType);
                                    }
                                    e.target.value = '';
                                  }}
                                />
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
                                Thêm {(requiredDoc.name || toDocTypeLabel(docType)).toLowerCase()}
                              </label>
                            )}
                          </div>
                        </div>
                      );
                    })}

                    {/* Render extra docs that might have been uploaded but are not strictly required */}
                    {supportingDocuments.filter(d => !request.documentRequirements?.requiredDocTypes.includes(d.docType)).map(extraDoc => {
                      const docType = extraDoc.docType;
                      if (supportingDocuments.findIndex(d => d.docType === docType) !== supportingDocuments.indexOf(extraDoc)) return null;

                      const docsForType = supportingDocuments.filter(d => d.docType === docType);
                      return (
                        <div key={docType} className="soft-card" style={{ padding: '1.25rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <h3 style={{ fontSize: '1rem', margin: 0 }}>{toDocTypeLabel(docType)} (Bổ sung)</h3>
                            <span className="status-badge badge-green">
                              {docsForType.length} tệp
                            </span>
                          </div>

                          <div className="list-stack" style={{ gap: '0.5rem' }}>
                            {docsForType.map(item => (
                              <div key={item.documentId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(0,0,0,0.02)', padding: '0.5rem 0.75rem', borderRadius: '6px' }}>
                                <span style={{ fontSize: '0.85rem', wordBreak: 'break-all', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#64748b" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>
                                  {getDocumentFileName(item)}
                                </span>
                                <div style={{ display: 'flex', gap: '0.25rem', alignItems: 'center' }}>
                                  <button type="button" className="ghost-btn" style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem', minWidth: 'auto', color: 'var(--primary)' }} onClick={() => handleView(item)}>Xem</button>
                                  <button type="button" className="ghost-btn" style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem', minWidth: 'auto' }} onClick={() => handleDownload(item)}>Tải về</button>
                                  {!isTerminalStatus && (
                                    <label className="ghost-btn" style={{ padding: '0.25rem 0.5rem', fontSize: '0.8rem', minWidth: 'auto', color: '#f59e0b', cursor: uploading ? 'wait' : 'pointer', margin: 0, fontWeight: 500 }}>
                                      Cập nhật
                                      <input type="file" style={{ display: 'none' }} disabled={uploading} onChange={(e) => {
                                        const file = e.target.files?.[0];
                                        if (file) handleReplace(file, item.documentId);
                                        e.target.value = '';
                                      }} />
                                    </label>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </section>
              </div>
            </div>
          </>
        ) : null}

        {viewingDocUrl && (
          <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: '#333', zIndex: 9999, display: 'flex', flexDirection: 'column' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.5rem 1rem', background: '#1e293b' }}>
              <span style={{ color: 'white', fontWeight: 500 }}>Trình xem tài liệu trực tuyến</span>
              <button className="primary-btn" style={{ background: '#ef4444', padding: '0.4rem 1.5rem' }} onClick={() => { URL.revokeObjectURL(viewingDocUrl); setViewingDocUrl(null); }}>
                Đóng (X)
              </button>
            </div>
            <iframe src={viewingDocUrl} style={{ width: '100%', flex: 1, border: 'none', background: '#e2e8f0' }} title="Trình xem tài liệu" />
          </div>
        )}

        {showCancelConfirm && (
          <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(15, 23, 42, 0.6)', backdropFilter: 'blur(4px)', zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
            <div className="soft-card" style={{ width: '100%', maxWidth: '400px', padding: '2rem', textAlign: 'center' }}>
              <div style={{ width: '48px', height: '48px', borderRadius: '50%', background: '#fee2e2', color: '#ef4444', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 1.5rem auto' }}>
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
              </div>
              <h3 style={{ margin: '0 0 0.5rem 0', fontSize: '1.25rem' }}>Hủy yêu cầu công chứng</h3>
              <p className="muted-text" style={{ marginBottom: '2rem' }}>Bạn có chắc chắn muốn hủy yêu cầu này không? Hành động này <b>không thể hoàn tác</b>.</p>
              
              <div style={{ display: 'flex', gap: '1rem' }}>
                <button type="button" className="ghost-btn" style={{ flex: 1 }} onClick={() => setShowCancelConfirm(false)}>Quay lại</button>
                <button type="button" className="primary-btn" style={{ flex: 1, background: '#ef4444', borderColor: '#ef4444', color: 'white' }} onClick={() => {
                  setShowCancelConfirm(false);
                  executeCancel();
                }}>Xác nhận Hủy</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
}
