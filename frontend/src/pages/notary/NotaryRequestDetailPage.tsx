import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { DashboardLayout } from '../../components/DashboardLayout';
import { listActiveOfficesApi } from '../../features/admin/adminApi';
import {
  acceptRequestApi,
  downloadDocumentApi,
  getRequestApi,
  getRequestDocumentsApi,
  rejectRequestApi,
  scheduleRequestApi,
  uploadRequestDocumentApi,
  viewDocumentApi,
} from '../../features/requests/requestApi';
import { toApiErrorMessage } from '../../lib/apiError';
import { getVideoRoomPathFromMeetingUrl } from '../../lib/videoRoom';
import {
  toContractTypeLabel,
  toDocTypeLabel,
  toRequestStatusMeta,
  toServiceTypeLabel,
} from '../../lib/enumLabels';
import type { NotaryOffice } from '../../types/admin';
import type { DocType, DocumentItem, NotaryRequest } from '../../types/request';

type ViewerItem = {
  url: string | null;
  title: string;
  meta: string;
  document?: DocumentItem;
  source: 'document';
};

type DocumentGroup = {
  docType: DocType;
  label: string;
  required: boolean;
  missing: boolean;
  systemGenerated?: boolean;
  documents: DocumentItem[];
};

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('vi-VN');
}

function toDateTimeLocalValue(date: Date) {
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return localDate.toISOString().slice(0, 16);
}

export function NotaryRequestDetailPage() {
  const { id = '' } = useParams();
  const [request, setRequest] = useState<NotaryRequest | null>(null);
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [scheduleWarning, setScheduleWarning] = useState('');
  const [scheduleDateTime, setScheduleDateTime] = useState('');
  const [scheduleOfficeId, setScheduleOfficeId] = useState('');
  const [showOfficePicker, setShowOfficePicker] = useState(false);
  const [offices, setOffices] = useState<NotaryOffice[]>([]);
  const [officeLoading, setOfficeLoading] = useState(false);
  const [officeError, setOfficeError] = useState('');
  const [draftFile, setDraftFile] = useState<File | null>(null);
  const [viewerItem, setViewerItem] = useState<ViewerItem | null>(null);
  const [viewerExpanded, setViewerExpanded] = useState(false);
  const scheduleInputRef = useRef<HTMLInputElement | null>(null);
  const draftInputRef = useRef<HTMLInputElement | null>(null);

  const statusInfo = useMemo(() => {
    if (!request) return null;
    return toRequestStatusMeta(request.status);
  }, [request]);

  const reload = async () => {
    if (!id) {
      return;
    }

    const [requestData, documentData] = await Promise.all([getRequestApi(id), getRequestDocumentsApi(id)]);
    setRequest(requestData);
    setDocuments(documentData);
  };

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        await reload();
      } catch (loadError) {
        setError(toApiErrorMessage(loadError, 'Không tải được chi tiết yêu cầu'));
      } finally {
        setLoading(false);
      }
    };

    void load();
  }, [id]);

  useEffect(() => {
    return () => {
      if (viewerItem?.url) {
        URL.revokeObjectURL(viewerItem.url);
      }
    };
  }, [viewerItem?.url]);

  const selectedScheduleOffice = useMemo(
    () => offices.find((office) => office.id === scheduleOfficeId) ?? null,
    [offices, scheduleOfficeId],
  );

  useEffect(() => {
    const loadOffices = async () => {
      setOfficeLoading(true);
      setOfficeError('');
      try {
        setOffices(await listActiveOfficesApi());
      } catch (loadError) {
        setOfficeError(toApiErrorMessage(loadError, 'Không tải được danh sách văn phòng công chứng'));
      } finally {
        setOfficeLoading(false);
      }
    };

    void loadOffices();
  }, []);

  const requiresTemplate = request?.requiresTemplate !== false;

  const handleAccept = async () => {
    if (requiresTemplate && !draftDocument && !draftFile) {
      setError('Vui lòng tải lên file mẫu văn bản PDF hoặc DOCX trước khi tiếp nhận');
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      if (requiresTemplate && draftFile) {
        await uploadRequestDocumentApi(id, draftFile, 'DRAFT_CONTRACT');
        setDraftFile(null);
        if (draftInputRef.current) draftInputRef.current.value = '';
      }
      await acceptRequestApi(id);
      await reload();
    } catch (actionError) {
      setError(toApiErrorMessage(actionError, 'Không thể tiếp nhận yêu cầu'));
    } finally {
      setSubmitting(false);
    }
  };

  const executeReject = async () => {
    if (!rejectReason.trim()) {
      setError('Vui lòng nhập lý do từ chối');
      return;
    }

    setSubmitting(true);
    setError('');
    try {
      await rejectRequestApi(id, rejectReason.trim());
      setRejectReason('');
      setShowRejectModal(false);
      await reload();
    } catch (actionError) {
      setError(toApiErrorMessage(actionError, 'Không thể từ chối yêu cầu'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSchedule = async () => {
    const effectiveScheduleDateTime = scheduleDateTime || scheduleInputRef.current?.value || '';
    if (!effectiveScheduleDateTime) {
      setError('');
      setScheduleWarning('Vui lòng chọn thời gian hẹn');
      return;
    }

    if (!scheduleDateTime && effectiveScheduleDateTime) {
      setScheduleDateTime(effectiveScheduleDateTime);
    }

    const selectedTime = new Date(effectiveScheduleDateTime);
    if (Number.isNaN(selectedTime.getTime()) || selectedTime <= new Date()) {
      setScheduleWarning('Thời gian hẹn phải ở tương lai. Vui lòng chọn thời điểm muộn hơn hiện tại.');
      return;
    }

    if (request?.serviceType === 'OFFLINE' && !selectedScheduleOffice) {
      setError('');
      setScheduleWarning('Vui lòng chọn văn phòng công chứng để gặp mặt');
      return;
    }

    if (requiresTemplate && !draftDocument) {
      setError('');
      setScheduleWarning('Vui lòng tải lên file mẫu văn bản trước khi lên lịch hẹn');
      return;
    }

    setSubmitting(true);
    setError('');
    setScheduleWarning('');
    try {
      const isoTime = new Date(effectiveScheduleDateTime).toISOString();
      await scheduleRequestApi(id, isoTime, request?.serviceType === 'OFFLINE' ? selectedScheduleOffice?.address : undefined);
      await reload();
    } catch (actionError) {
      setError(toApiErrorMessage(actionError, 'Không thể lên lịch hẹn'));
    } finally {
      setSubmitting(false);
    }
  };

  const missingDocTypes = request?.documentRequirements?.missingDocTypes ?? [];
  const draftDocument = useMemo(
    () => documents.find((item) => item.docType === 'DRAFT_CONTRACT') ?? null,
    [documents],
  );
  const missingDocTypesForAccept = useMemo(
    () => missingDocTypes.filter((docType) => !requiresTemplate || docType !== 'DRAFT_CONTRACT'),
    [missingDocTypes, requiresTemplate],
  );
  const canAccept = request?.status === 'PROCESSING'
    && missingDocTypesForAccept.length === 0
    && (!requiresTemplate || !!draftDocument || !!draftFile);
  const videoRoomPath = useMemo(
    () => getVideoRoomPathFromMeetingUrl(request?.meetingUrl),
    [request?.meetingUrl],
  );
  const canJoinVideoSession = !!request
    && request.serviceType === 'ONLINE'
    && (request.status === 'SCHEDULED' || request.status === 'IN_VIDEO_CALL')
    && request.appointment?.status === 'PENDING'
    && !!videoRoomPath;
  const requiredDocTypes = request?.documentRequirements?.requiredDocTypes ?? [];
  const compactRequestId = request?.requestId.split('-')[0] ?? '';
  const getFileName = (filePath: string) => filePath.split('/').pop() || filePath;
  const getDocumentFileName = (item: DocumentItem) =>
    item.displayName || item.originalFileName || getFileName(item.filePath) || `${item.documentId}.bin`;
  const getFileExtension = (filePath: string) => {
    const fileName = getFileName(filePath);
    const extension = fileName.includes('.') ? fileName.split('.').pop() : '';
    return extension ? extension.toUpperCase() : 'TỆP';
  };
  const getDocumentExtension = (item: DocumentItem) => {
    const name = item.originalFileName || item.displayName || item.filePath;
    const extension = name.includes('.') ? name.split('.').pop() : '';
    return extension ? extension.toLowerCase() : '';
  };
  const canPreviewDocument = (item: DocumentItem) => {
    const contentType = item.contentType?.toLowerCase() || '';
    const extension = getDocumentExtension(item);
    return contentType === 'application/pdf'
      || contentType.startsWith('image/')
      || ['pdf', 'jpg', 'jpeg', 'png'].includes(extension);
  };
  const getDocumentDisplayName = (item: DocumentItem, sequence: number) => {
    const label = toDocTypeLabel(item.docType) || 'Tài liệu';
    return sequence > 1 ? `${label} ${String(sequence).padStart(2, '0')}` : label;
  };
  const documentGroups = useMemo<DocumentGroup[]>(() => {
    const requirementDetails = request?.documentRequirements?.requiredDocuments ?? [];
    const requiredGroups = requiredDocTypes.map((docType) => {
      const detail = requirementDetails.find((item) => item.code === docType);
      const groupDocuments = documents.filter((documentItem) => documentItem.docType === docType);
      const systemGenerated = detail?.source === 'SYSTEM_GENERATED';
      return {
        docType,
        label: detail?.name || toDocTypeLabel(docType),
        required: true,
        missing: systemGenerated ? false : detail?.missing ?? groupDocuments.length === 0,
        systemGenerated,
        documents: groupDocuments,
      };
    });
    const requiredSet = new Set(requiredDocTypes);
    const extraDocTypes = Array.from(
      new Set(documents.filter((documentItem) => !requiredSet.has(documentItem.docType)).map((documentItem) => documentItem.docType)),
    );
    const extraGroups = extraDocTypes.map((docType) => ({
      docType,
      label: toDocTypeLabel(docType),
      required: false,
      missing: false,
      documents: documents.filter((documentItem) => documentItem.docType === docType),
    }));

    return [...requiredGroups, ...extraGroups];
  }, [documents, request?.documentRequirements?.requiredDocuments, requiredDocTypes]);
  const completedRequiredCount = documentGroups.filter((group) => group.required && !group.missing).length;
  const requiredSummary = requiredDocTypes.length > 0
    ? `${completedRequiredCount}/${requiredDocTypes.length}`
    : `${documents.length} tài liệu`;

  const openViewer = (item: ViewerItem) => {
    setViewerItem((current) => {
      if (current?.url) {
        URL.revokeObjectURL(current.url);
      }
      return item;
    });
    setViewerExpanded(false);
  };

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

  const handleView = async (item: DocumentItem, title?: string) => {
    if (!canPreviewDocument(item)) {
      setError('');
      openViewer({
        url: null,
        title: title ?? getDocumentDisplayName(item, 1),
        meta: `${toDocTypeLabel(item.docType)} Â· ${getFileExtension(item.filePath)} Â· ${formatDate(item.createdAt)}`,
        document: item,
        source: 'document',
      });
      return;
    }

    try {
      setError('');
      const blob = await viewDocumentApi(item.documentId);
      let finalBlob = blob;

      const path = item.filePath.toLowerCase();
      if (path.endsWith('.pdf') && blob.type !== 'application/pdf') {
        finalBlob = new Blob([blob], { type: 'application/pdf' });
      } else if ((path.endsWith('.jpg') || path.endsWith('.jpeg')) && blob.type !== 'image/jpeg') {
        finalBlob = new Blob([blob], { type: 'image/jpeg' });
      } else if (path.endsWith('.png') && blob.type !== 'image/png') {
        finalBlob = new Blob([blob], { type: 'image/png' });
      }

      const objectUrl = URL.createObjectURL(finalBlob);
      openViewer({
        url: objectUrl,
        title: title ?? getDocumentDisplayName(item, 1),
        meta: `${toDocTypeLabel(item.docType)} · ${getFileExtension(item.filePath)} · ${formatDate(item.createdAt)}`,
        source: 'document',
      });
    } catch (viewError) {
      setError(toApiErrorMessage(viewError, 'Không xem được tài liệu'));
    }
  };

  const closeViewer = () => {
    if (viewerItem?.url) {
      URL.revokeObjectURL(viewerItem.url);
    }
    setViewerItem(null);
    setViewerExpanded(false);
  };

  const renderDraftUploader = () => (
    <div className="template-picker draft-upload-panel">
      <div className={`template-picker-summary ${draftDocument || draftFile ? '' : 'missing'}`}>
        <div>
          <div className="template-picker-summary-name">
            {draftFile?.name || draftDocument?.displayName || draftDocument?.originalFileName || 'Chưa có file mẫu văn bản'}
          </div>
          <div className="template-picker-summary-meta">
            {draftFile
              ? 'File mới sẽ được tải lên khi tiếp nhận'
              : draftDocument
                ? `${getFileExtension(draftDocument.filePath)} · ${formatDate(draftDocument.createdAt)}`
                : 'Tải lên PDF, DOC hoặc DOCX trước khi tiếp nhận'}
          </div>
        </div>
        {draftDocument ? (
          <button
            type="button"
            className="template-view-btn"
            onClick={() => void handleView(draftDocument, 'Mẫu văn bản hồ sơ')}
          >
            Xem
          </button>
        ) : null}
      </div>
      <input
        ref={draftInputRef}
        type="file"
        className="template-file-input"
        accept=".pdf,.doc,.docx"
        onChange={(event) => setDraftFile(event.target.files?.[0] ?? null)}
      />
      <button
        type="button"
        className={`template-file-picker ${draftFile || draftDocument ? 'has-file' : ''}`}
        onClick={() => draftInputRef.current?.click()}
        disabled={submitting || request?.status !== 'PROCESSING'}
      >
        <span className="template-file-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
            <line x1="12" y1="18" x2="12" y2="11" />
            <polyline points="9 14 12 11 15 14" />
          </svg>
        </span>
        <span className="template-file-copy">
          <strong>{draftDocument ? 'Thay file mẫu văn bản' : 'Chọn file mẫu văn bản'}</strong>
          <small>PDF, DOC hoặc DOCX</small>
        </span>
      </button>
    </div>
  );

  const renderViewerContent = () => {
    if (!viewerItem) {
      return (
        <div className="document-inspector-empty">
          <span>Chưa có tài liệu đang mở</span>
        </div>
      );
    }

    if (viewerItem.url) {
      return <iframe src={viewerItem.url} title={viewerItem.title} />;
    }

    return (
      <div className="document-inspector-empty">
        <span>Tệp DOC/DOCX không thể xem trực tiếp trong trình duyệt.</span>
        {viewerItem.document ? (
          <button type="button" className="primary-btn" onClick={() => void handleDownload(viewerItem.document!)}>
            Tải về
          </button>
        ) : null}
      </div>
    );
  };

  const renderViewerPanel = () => (
    <section className={`document-inspector ${viewerItem ? 'active' : ''}`}>
      <div className="document-inspector-head">
        <div>
          <span className="section-kicker">Trình xem hồ sơ</span>
          <h2>{viewerItem?.title || 'Chọn tài liệu để xem'}</h2>
          {viewerItem ? <p>{viewerItem.meta}</p> : <p>Xem nhanh tài liệu hoặc dự thảo hợp đồng ngay trong trang để đối chiếu hồ sơ.</p>}
        </div>
        {viewerItem ? (
          <div className="document-inspector-actions">
            <button type="button" className="ghost-btn" onClick={() => setViewerExpanded(true)}>
              Phóng to
            </button>
            <button type="button" className="ghost-btn" onClick={closeViewer}>
              Đóng
            </button>
          </div>
        ) : null}
      </div>
      {viewerItem ? renderViewerContent() : (
        <div className="document-inspector-empty">
          <span>Chưa có tài liệu đang mở</span>
        </div>
      )}
    </section>
  );

  return (
    <DashboardLayout role="notary">
      <div className="page-content notary-detail-page">
        {loading ? <p className="muted-text">Đang tải dữ liệu...</p> : null}
        {error ? <div className="form-error">{error}</div> : null}

        {!loading && request ? (
          <>
            <div className="request-detail-hero">
              <div className="request-detail-title">
                <span>Hồ sơ công chứng</span>
                <h1>{toContractTypeLabel(request.contractType)}</h1>
                <p>
                  Mã yêu cầu <b>{compactRequestId}</b> · Tạo lúc {formatDate(request.createdAt)}
                </p>
              </div>
              <div className="request-detail-status">
                {statusInfo ? <span className={`status-badge ${statusInfo.tone}`}>{statusInfo.label}</span> : null}
                <span>{toServiceTypeLabel(request.serviceType)}</span>
              </div>
            </div>

            <div className="detail-layout">
              <div className="detail-main">
                <div className="detail-surface">
                  <section className="detail-section">
                    <div className="section-heading-row">
                      <div>
                        <span className="section-kicker">Nội dung chính</span>
                        <h2>Tổng quan yêu cầu</h2>
                      </div>
                      <span className="detail-count-pill">{documents.length} tài liệu</span>
                    </div>
                    <div className="request-summary-grid">
                      <article>
                        <span>Loại hợp đồng</span>
                        <strong>{toContractTypeLabel(request.contractType)}</strong>
                      </article>
                      <article>
                        <span>Hình thức</span>
                        <strong>{toServiceTypeLabel(request.serviceType)}</strong>
                      </article>
                      <article>
                        <span>Hồ sơ bắt buộc</span>
                        <strong>{requiredSummary}</strong>
                      </article>
                    </div>
                    <div className="request-progress-panel">
                      <div>
                        <span>Tiến độ thẩm định</span>
                        <strong>
                          {missingDocTypesForAccept.length > 0
                            ? `Còn thiếu ${missingDocTypesForAccept.length} nhóm hồ sơ`
                            : 'Hồ sơ đã đủ để xử lý'}
                        </strong>
                      </div>
                      <div className="request-progress-track" aria-hidden="true">
                        <span
                          style={{
                            width: `${requiredDocTypes.length > 0 ? Math.round((completedRequiredCount / requiredDocTypes.length) * 100) : 100}%`,
                          }}
                        />
                      </div>
                    </div>
                    <div className="request-description">
                      <span>Mô tả thêm</span>
                      <p>{request.description || 'Không có mô tả'}</p>
                    </div>
                  </section>

                  {renderViewerPanel()}
                </div>
              </div>

              <div className="detail-sidebar">
                <div className="sticky-panel">
                  {missingDocTypesForAccept.length > 0 ? (
                    <div className="alert-box alert-warning">
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
                        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
                        <line x1="12" y1="9" x2="12" y2="13"></line>
                        <line x1="12" y1="17" x2="12.01" y2="17"></line>
                      </svg>
                      <div>
                        <strong>Thiếu hồ sơ</strong>
                        <p style={{ marginTop: '0.25rem', fontSize: '0.9rem' }}>{missingDocTypesForAccept.map((t) => toDocTypeLabel(t)).join(', ')}</p>
                      </div>
                    </div>
                  ) : request.status === 'PROCESSING' ? (
                    <div className="alert-box alert-success">
                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
                        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                        <polyline points="22 4 12 14.01 9 11.01"></polyline>
                      </svg>
                      <div>
                        <strong>Đủ điều kiện</strong>
                        <p style={{ marginTop: '0.25rem', fontSize: '0.9rem' }}>Hồ sơ đã đủ giấy tờ tối thiểu.</p>
                      </div>
                    </div>
                  ) : null}

                  <section className="detail-panel">
                    <h2 style={{ marginBottom: '1rem' }}>Hành động</h2>

                    {request.status === 'ACCEPTED' ? (
                      <div>
                        <h3 style={{ marginBottom: '1rem', fontSize: '1.1rem' }}>Lên lịch hẹn</h3>

                        {requiresTemplate ? (
                          <div style={{ marginBottom: '1rem' }}>{renderDraftUploader()}</div>
                        ) : (
                          <div className="alert-box alert-info" style={{ marginBottom: '1rem' }}>
                            Dịch vụ này không cần mẫu văn bản. Lịch hẹn sẽ mở luồng đối soát giấy tờ.
                          </div>
                        )}

                        <label className="field" style={{ marginBottom: '1rem' }}>
                          <span>Thời gian hẹn *</span>
                          <input
                            type="datetime-local"
                            ref={scheduleInputRef}
                            value={scheduleDateTime}
                            min={toDateTimeLocalValue(new Date())}
                            onChange={(event) => setScheduleDateTime(event.target.value)}
                          />
                        </label>

                        {request.serviceType === 'OFFLINE' ? (
                          <div className="field" style={{ marginBottom: '1.5rem' }}>
                            <span>Văn phòng công chứng *</span>
                            <div className="office-picker">
                              <button
                                type="button"
                                className={`office-picker-trigger ${showOfficePicker ? 'open' : ''}`}
                                onClick={() => setShowOfficePicker((open) => !open)}
                                disabled={officeLoading || offices.length === 0}
                              >
                                <span>
                                  {officeLoading
                                    ? 'Đang tải văn phòng...'
                                    : selectedScheduleOffice?.name || 'Chọn văn phòng công chứng'}
                                </span>
                                <svg viewBox="0 0 24 24" aria-hidden="true">
                                  <polyline points="6 9 12 15 18 9"></polyline>
                                </svg>
                              </button>

                              {showOfficePicker && offices.length > 0 ? (
                                <div className="office-picker-menu">
                                  {offices.map((office) => (
                                    <button
                                      key={office.id}
                                      type="button"
                                      className={`office-picker-option ${office.id === scheduleOfficeId ? 'selected' : ''}`}
                                      onClick={() => {
                                        setScheduleOfficeId(office.id);
                                        setShowOfficePicker(false);
                                      }}
                                    >
                                      <span className="office-picker-name">{office.name}</span>
                                      <span className="office-picker-address">{office.address}</span>
                                      {office.phoneNumber || office.workingHours ? (
                                        <span className="office-picker-meta">
                                          {[office.phoneNumber, office.workingHours].filter(Boolean).join(' · ')}
                                        </span>
                                      ) : null}
                                    </button>
                                  ))}
                                </div>
                              ) : null}
                            </div>
                            {selectedScheduleOffice ? (
                              <div className="office-picker-selected">
                                <b>{selectedScheduleOffice.name}</b>
                                <span>{selectedScheduleOffice.address}</span>
                              </div>
                            ) : null}
                            {officeError ? (
                              <span style={{ color: '#dc2626', fontSize: '0.85rem', marginTop: '0.35rem' }}>{officeError}</span>
                            ) : offices.length === 0 && !officeLoading ? (
                              <span style={{ color: '#dc2626', fontSize: '0.85rem', marginTop: '0.35rem' }}>
                                Chưa có văn phòng công chứng đang áp dụng. Vui lòng liên hệ quản trị viên.
                              </span>
                            ) : null}
                          </div>
                        ) : null}

                        <button
                          type="button"
                          className="primary-btn w-full"
                          onClick={handleSchedule}
                          disabled={submitting || (requiresTemplate && !draftDocument) || (request.serviceType === 'OFFLINE' && (officeLoading || offices.length === 0))}
                        >
                          {submitting ? 'Đang xử lý...' : 'Xác nhận lịch hẹn'}
                        </button>
                      </div>
                    ) : ['NEW', 'PROCESSING'].includes(request.status) ? (
                      <div className="notary-accept-stack">
                        {requiresTemplate ? (
                          <section className="accept-step-card">
                            <span className="section-kicker">Mẫu văn bản</span>
                            {renderDraftUploader()}
                          </section>
                        ) : (
                          <div className="alert-box alert-info">
                            Dịch vụ này không cần mẫu văn bản. Công chứng viên sẽ đối soát giấy tờ và ký số chứng thực trong phiên.
                          </div>
                        )}
                        <div className="notary-action-buttons">
                          <button
                            type="button"
                            className="primary-btn w-full"
                            onClick={handleAccept}
                            disabled={submitting || !canAccept}
                            title={
                              request.status !== 'PROCESSING'
                                ? 'Chỉ tiếp nhận khi ở trạng thái chờ xử lý'
                                : missingDocTypesForAccept.length > 0
                                  ? 'Hồ sơ chưa đủ giấy tờ bắt buộc'
                                  : requiresTemplate && !draftDocument && !draftFile
                                    ? 'Cần tải lên file mẫu văn bản'
                                  : undefined
                            }
                          >
                            Tiếp nhận yêu cầu
                          </button>
                          <button
                            type="button"
                            className="ghost-btn w-full danger-soft-btn"
                            onClick={() => setShowRejectModal(true)}
                            disabled={submitting}
                          >
                            Từ chối yêu cầu
                          </button>
                        </div>
                        {request.status === 'NEW' && !canAccept && (
                          <p className="muted-text" style={{ fontSize: '0.85rem', textAlign: 'center', marginTop: '0.5rem' }}>
                            Vui lòng đợi khách hàng bổ sung đủ hồ sơ.
                          </p>
                        )}
                      </div>
                    ) : request.status === 'SCHEDULED' || request.status === 'IN_VIDEO_CALL' ? (
                      <div style={{ textAlign: 'center' }}>
                        <div className="alert-box alert-info" style={{ marginBottom: '1rem', textAlign: 'left' }}>
                          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
                            <circle cx="12" cy="12" r="10"></circle>
                            <polyline points="12 6 12 12 16 14"></polyline>
                          </svg>
                          <span style={{ fontSize: '0.95rem' }}>
                            {request.status === 'IN_VIDEO_CALL'
                              ? 'Phiên xác thực danh tính qua video đang diễn ra.'
                              : request.serviceType === 'ONLINE'
                                ? 'Lịch hẹn trực tuyến đã được xác nhận. Vui lòng tham gia đúng giờ.'
                                : 'Lịch hẹn trực tiếp đã được xác nhận. Vui lòng gặp khách hàng tại văn phòng đúng giờ.'}
                          </span>
                        </div>
                        {canJoinVideoSession ? (
                          <Link to={videoRoomPath!} className="primary-btn w-full" style={{ justifyContent: 'center' }}>
                            {request.status === 'IN_VIDEO_CALL' ? 'Tham gia phiên đối soát' : 'Mở phiên đối soát'}
                          </Link>
                        ) : null}
                      </div>
                    ) : (
                      <p className="muted-text" style={{ fontSize: '0.95rem', textAlign: 'center', padding: '1rem 0' }}>
                        Không có hành động khả dụng.
                      </p>
                    )}
                  </section>

                  <section className="detail-panel document-sidebar-panel">
                    <div className="section-heading-row">
                      <div>
                        <span className="section-kicker">Tài liệu khách hàng</span>
                        <h2>Hồ sơ đính kèm</h2>
                      </div>
                      {missingDocTypesForAccept.length > 0 ? (
                        <span className="detail-count-pill warning">Thiếu {missingDocTypesForAccept.length}</span>
                      ) : (
                        <span className="detail-count-pill success">Đủ hồ sơ</span>
                      )}
                    </div>
                    {documentGroups.length === 0 ? <p className="muted-text">Khách hàng chưa tải lên tài liệu nào.</p> : null}
                    <div className="document-group-list compact">
                      {documentGroups.map((group) => (
                        <div className={`document-group-card ${group.missing ? 'missing' : ''}`} key={group.docType}>
                          <div className="document-group-head">
                            <div>
                              <h3>{group.label}</h3>
                              <p>{group.required ? 'Hồ sơ bắt buộc' : 'Tài liệu bổ sung'}</p>
                            </div>
                            <span className={`document-group-status ${group.missing ? 'warning' : 'success'}`}>
                              {group.systemGenerated ? 'Hệ thống tạo' : group.missing ? 'Còn thiếu' : `${group.documents.length} tệp`}
                            </span>
                          </div>
                          {group.documents.length > 0 ? (
                            <div className="document-file-list">
                              {group.documents.map((item, index) => {
                                const documentTitle = getDocumentDisplayName(item, index + 1);
                                const storedFileName = getFileName(item.filePath);
                                const originalFileName = getDocumentFileName(item);

                                return (
                                  <div className="doc-card" key={item.documentId}>
                                    <div className="doc-icon-wrap">
                                      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                                        <polyline points="14 2 14 8 20 8"></polyline>
                                        <line x1="16" y1="13" x2="8" y2="13"></line>
                                        <line x1="16" y1="17" x2="8" y2="17"></line>
                                        <polyline points="10 9 9 9 8 9"></polyline>
                                      </svg>
                                    </div>
                                    <div className="doc-info">
                                      <h3 title={`Tệp gốc: ${originalFileName}${storedFileName !== originalFileName ? ` | Tệp lưu trữ: ${storedFileName}` : ''}`}>{documentTitle}</h3>
                                      <p className="doc-meta">
                                        <span title={originalFileName}>{originalFileName}</span>
                                        <span>{getFileExtension(item.filePath)}</span>
                                        <span>{formatDate(item.createdAt)}</span>
                                      </p>
                                    </div>
                                    <div className="doc-actions">
                                      <button type="button" className="ghost-btn" onClick={() => void handleView(item, documentTitle)}>Xem</button>
                                      <button type="button" className="link-btn" onClick={() => void handleDownload(item)}>Tải</button>
                                    </div>
                                  </div>
                                );
                              })}
                            </div>
                          ) : (
                            <div className="document-missing-state">
                              {group.systemGenerated ? 'Tài liệu này được hệ thống ghi nhận tự động.' : 'Chưa có tài liệu cho nhóm này.'}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </section>
                </div>
              </div>
            </div>
          </>
        ) : null}

        {viewerItem && viewerExpanded ? (
          <div className="document-viewer-modal">
            <div className="document-viewer-modal-head">
              <div>
                <span>Tài liệu hồ sơ</span>
                <strong>{viewerItem.title}</strong>
              </div>
              <div>
                <button className="ghost-btn" onClick={() => setViewerExpanded(false)}>
                  Thu nhỏ
                </button>
                <button className="primary-btn" onClick={closeViewer}>
                  Đóng
                </button>
              </div>
            </div>
            {renderViewerContent()}
          </div>
        ) : null}

        {showRejectModal ? (
          <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(15, 23, 42, 0.6)', backdropFilter: 'blur(4px)', zIndex: 9999, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1rem' }}>
            <div className="soft-card" style={{ width: '100%', maxWidth: '520px', padding: '2.5rem' }}>
              <h3 style={{ margin: 0, fontSize: '1.5rem' }}>Từ chối yêu cầu</h3>
              <p className="muted-text" style={{ marginTop: '0.5rem', marginBottom: '1.5rem' }}>
                Vui lòng nhập lý do từ chối để khách hàng có thể bổ sung hoặc điều chỉnh hồ sơ.
              </p>

              <div className="field">
                <span>Lý do từ chối *</span>
                <textarea
                  rows={4}
                  value={rejectReason}
                  onChange={(event) => setRejectReason(event.target.value)}
                  placeholder="Ví dụ: Thiếu giấy tờ tùy thân, thông tin chưa khớp..."
                />
              </div>

              <div style={{ display: 'flex', gap: '1rem', marginTop: '2rem' }}>
                <button type="button" className="ghost-btn" style={{ flex: 1 }} onClick={() => setShowRejectModal(false)} disabled={submitting}>
                  Hủy
                </button>
                <button type="button" className="primary-btn" style={{ flex: 1, background: '#ef4444' }} onClick={() => void executeReject()} disabled={submitting}>
                  {submitting ? 'Đang xử lý...' : 'Xác nhận từ chối'}
                </button>
              </div>
            </div>
          </div>
        ) : null}

        {scheduleWarning ? (
          <div className="schedule-warning-backdrop">
            <div className="schedule-warning-dialog" role="alertdialog" aria-modal="true" aria-labelledby="schedule-warning-title">
              <div className="schedule-warning-icon">
                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"></circle>
                  <polyline points="12 6 12 12 16 14"></polyline>
                </svg>
              </div>
              <h3 id="schedule-warning-title">Lịch hẹn không hợp lệ</h3>
              <p>{scheduleWarning}</p>
              <button type="button" className="primary-btn" onClick={() => setScheduleWarning('')}>
                Chọn lại thời gian
              </button>
            </div>
          </div>
        ) : null}
      </div>
    </DashboardLayout>
  );
}

