import { FormEvent, useEffect, useMemo, useState } from 'react';
import { DashboardLayout } from '../../components/DashboardLayout';
import {
  createDocumentRequirementConfigApi,
  deleteServiceApi,
  listDocumentRequirementConfigsApi,
  listDocumentTypesApi,
  updateServiceApi,
  updateDocumentRequirementConfigApi,
} from '../../features/admin/adminApi';
import { toApiErrorMessage } from '../../lib/apiError';
import { toDocTypeLabel } from '../../lib/enumLabels';
import type { DocumentRequirementConfig, DocumentRequirementConfigRequest, DocumentType } from '../../types/admin';

const emptyForm: DocumentRequirementConfigRequest = {
  serviceCode: '',
  serviceName: '',
  description: '',
  basePrice: 0,
  isActive: true,
  requiresTemplate: true,
  requiredDocTypes: [],
};

function formatPrice(value: number) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
}

function getDocTypeName(documentTypes: DocumentType[], code: string) {
  return documentTypes.find((item) => item.code === code)?.name || toDocTypeLabel(code);
}

function getSourceLabel(source: string) {
  if (source === 'SYSTEM_GENERATED') return 'Hệ thống tạo';
  if (source === 'USER_UPLOAD') return 'Người dân tải lên';
  return source;
}

function compareDocTypes(a: DocumentType, b: DocumentType) {
  return (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.name.localeCompare(b.name, 'vi') || a.code.localeCompare(b.code);
}

function toFormValue(config: DocumentRequirementConfig): DocumentRequirementConfigRequest {
  return {
    serviceCode: config.serviceCode,
    serviceName: config.serviceName,
    description: config.description || '',
    basePrice: config.basePrice,
    isActive: config.isActive,
    requiresTemplate: config.requiresTemplate,
    requiredDocTypes: config.requiredDocTypes,
  };
}

function normalizeRequiredDocTypes(selected: string[], documentTypes: DocumentType[]) {
  return documentTypes
    .filter((docType) => selected.includes(docType.code))
    .map((docType) => docType.code);
}

export function AdminDocumentRequirementsPage() {
  const [configs, setConfigs] = useState<DocumentRequirementConfig[]>([]);
  const [documentTypes, setDocumentTypes] = useState<DocumentType[]>([]);
  const [form, setForm] = useState<DocumentRequirementConfigRequest>(emptyForm);
  const [editingServiceId, setEditingServiceId] = useState<string | null>(null);
  const [confirmStatusConfig, setConfirmStatusConfig] = useState<DocumentRequirementConfig | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [pageError, setPageError] = useState('');
  const [modalError, setModalError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const configurableDocTypes = useMemo(
    () => documentTypes
      .filter((item) => item.isActive && item.source !== 'INTERNAL' && item.code !== 'REQUEST_FORM')
      .sort(compareDocTypes),
    [documentTypes],
  );

  const activeCount = useMemo(() => configs.filter((item) => item.isActive).length, [configs]);

  const loadData = async () => {
    setLoading(true);
    setPageError('');
    try {
      const [configData, typeData] = await Promise.all([
        listDocumentRequirementConfigsApi(),
        listDocumentTypesApi(),
      ]);
      setConfigs(configData);
      setDocumentTypes(typeData);
    } catch (error) {
      setPageError(toApiErrorMessage(error, 'Không tải được cấu hình hồ sơ'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
  }, []);

  const selectedDocTypesFor = (config: DocumentRequirementConfig) =>
    configurableDocTypes.filter((docType) => config.requiredDocTypes.includes(docType.code));

  const openCreateModal = () => {
    setForm(emptyForm);
    setEditingServiceId(null);
    setModalError('');
    setPageError('');
    setSuccessMessage('');
    setModalOpen(true);
  };

  const openEditModal = (config: DocumentRequirementConfig) => {
    setForm(toFormValue(config));
    setEditingServiceId(config.serviceId);
    setModalError('');
    setPageError('');
    setSuccessMessage('');
    setModalOpen(true);
  };

  const closeModal = () => {
    if (saving) return;
    setModalOpen(false);
    setEditingServiceId(null);
    setForm(emptyForm);
    setModalError('');
  };

  const toggleDocType = (code: string) => {
    setModalError('');
    setForm((current) => {
      const next = current.requiredDocTypes.includes(code)
        ? current.requiredDocTypes.filter((item) => item !== code)
        : [...current.requiredDocTypes, code];
      return { ...current, requiredDocTypes: next };
    });
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const payload: DocumentRequirementConfigRequest = {
      ...form,
      serviceCode: form.serviceCode.trim().toUpperCase(),
      serviceName: form.serviceName.trim(),
      description: form.description.trim(),
      basePrice: Number(form.basePrice),
      requiredDocTypes: normalizeRequiredDocTypes(form.requiredDocTypes, configurableDocTypes),
    };

    if (payload.requiredDocTypes.length === 0) {
      setModalError('Mỗi dịch vụ cần ít nhất một loại giấy tờ bắt buộc.');
      return;
    }

    setSaving(true);
    setModalError('');
    setPageError('');
    setSuccessMessage('');
    try {
      const saved = editingServiceId
        ? await updateDocumentRequirementConfigApi(editingServiceId, payload)
        : await createDocumentRequirementConfigApi(payload);
      setConfigs((current) => {
        const exists = current.some((item) => item.serviceId === saved.serviceId);
        const next = exists
          ? current.map((item) => (item.serviceId === saved.serviceId ? saved : item))
          : [...current, saved];
        return next.sort((a, b) => a.serviceCode.localeCompare(b.serviceCode));
      });
      setSuccessMessage(editingServiceId ? 'Đã cập nhật cấu hình hồ sơ.' : 'Đã tạo cấu hình hồ sơ.');
      setModalOpen(false);
      setEditingServiceId(null);
      setForm(emptyForm);
    } catch (error) {
      setModalError(toApiErrorMessage(error, 'Không lưu được cấu hình hồ sơ'));
    } finally {
      setSaving(false);
    }
  };

  const handleStatusChange = async () => {
    if (!confirmStatusConfig) return;
    const serviceId = confirmStatusConfig.serviceId;
    setPageError('');
    setSuccessMessage('');
    try {
      if (confirmStatusConfig.isActive) {
        await deleteServiceApi(serviceId);
      } else {
        await updateServiceApi(serviceId, {
          serviceCode: confirmStatusConfig.serviceCode,
          name: confirmStatusConfig.serviceName,
          description: confirmStatusConfig.description || '',
          basePrice: confirmStatusConfig.basePrice,
          isActive: true,
          requiresTemplate: confirmStatusConfig.requiresTemplate,
        });
      }
      setConfigs((current) =>
        current.map((item) => (item.serviceId === serviceId ? { ...item, isActive: !confirmStatusConfig.isActive } : item)),
      );
      setSuccessMessage(confirmStatusConfig.isActive ? 'Đã vô hiệu hóa dịch vụ.' : 'Đã kích hoạt lại dịch vụ.');
      setConfirmStatusConfig(null);
    } catch (error) {
      setPageError(toApiErrorMessage(error, 'Không cập nhật được trạng thái dịch vụ'));
    }
  };

  return (
    <DashboardLayout role="admin">
      <div className="page-content document-requirements-page">
        <div className="page-header with-action requirements-hero">
          <div>
            <h1>Cấu hình hồ sơ</h1>
            <p>Quản lý dịch vụ công chứng, bảng giá và các giấy tờ bắt buộc trong cùng một cấu hình.</p>
          </div>
          <div className="requirements-hero-actions">
            <div className="requirements-summary-pill">{activeCount} dịch vụ đang áp dụng</div>
            <button type="button" className="primary-btn" onClick={openCreateModal}>Thêm dịch vụ</button>
          </div>
        </div>

        {pageError ? <div className="form-error requirements-alert">{pageError}</div> : null}
        {successMessage ? <div className="inline-success requirements-alert">{successMessage}</div> : null}

        <section className="requirements-panel">
          {loading ? (
            <p className="requirements-empty">Đang tải cấu hình...</p>
          ) : configs.length === 0 ? (
            <p className="requirements-empty">Chưa có dịch vụ công chứng nào.</p>
          ) : configurableDocTypes.length === 0 ? (
            <p className="requirements-empty">Chưa có loại giấy tờ nào có thể cấu hình.</p>
          ) : (
            <div className="requirements-table-wrap">
              <table className="requirements-table">
                <colgroup>
                  <col className="requirements-col-service" />
                  <col className="requirements-col-price" />
                  <col className="requirements-col-status" />
                  <col className="requirements-col-docs" />
                  <col className="requirements-col-actions" />
                </colgroup>
                <thead>
                  <tr>
                    <th>Dịch vụ</th>
                    <th>Mức phí</th>
                    <th>Trạng thái</th>
                    <th>Giấy tờ bắt buộc</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {configs.map((config) => {
                    const selectedDocTypes = selectedDocTypesFor(config);
                    const previewDocTypes = selectedDocTypes.slice(0, 3);
                    const remainingCount = selectedDocTypes.length - previewDocTypes.length;

                    return (
                      <tr key={config.serviceId}>
                        <td>
                          <div className="requirements-service-cell">
                            <strong>{config.serviceName}</strong>
                            <span>{config.serviceCode}</span>
                            <span>{config.requiresTemplate ? 'Cần mẫu văn bản' : 'Không cần mẫu văn bản'}</span>
                            {config.description ? <p>{config.description}</p> : null}
                          </div>
                        </td>
                        <td className="requirements-price">{formatPrice(config.basePrice)}</td>
                        <td>
                          <span className={`status-badge ${config.isActive ? 'badge-green' : 'badge-gray'}`}>
                            {config.isActive ? 'Đang áp dụng' : 'Ngừng áp dụng'}
                          </span>
                        </td>
                        <td>
                          <div className="requirements-doc-summary">
                            <strong>{selectedDocTypes.length} loại giấy tờ</strong>
                            <div>
                              {previewDocTypes.length > 0
                                ? previewDocTypes.map((docType) => (
                                  <span key={docType.code}>{docType.name || getDocTypeName(documentTypes, docType.code)}</span>
                                ))
                                : <span>Chưa chọn giấy tờ</span>}
                              {remainingCount > 0 ? <span>+{remainingCount}</span> : null}
                            </div>
                          </div>
                        </td>
                        <td className="requirements-actions">
                          <button type="button" className="ghost-btn" onClick={() => openEditModal(config)}>
                            Sửa
                          </button>
                          <button
                            type="button"
                            className="ghost-btn"
                            onClick={() => setConfirmStatusConfig(config)}
                          >
                            {config.isActive ? 'Vô hiệu hóa' : 'Kích hoạt lại'}
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>

      {modalOpen ? (
        <div
          className="requirements-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) closeModal();
          }}
        >
          <section className="requirements-modal wide" role="dialog" aria-modal="true" aria-labelledby="requirements-modal-title">
            <div className="requirements-modal-head">
              <div>
                <h2 id="requirements-modal-title">{editingServiceId ? 'Cập nhật cấu hình hồ sơ' : 'Thêm cấu hình hồ sơ'}</h2>
                <p>{editingServiceId ? form.serviceName : 'Tạo dịch vụ công chứng và bộ giấy tờ bắt buộc.'}</p>
              </div>
              <button type="button" className="ghost-btn" onClick={closeModal} disabled={saving}>Đóng</button>
            </div>

            <form className="requirements-config-form" onSubmit={handleSubmit}>
              {modalError ? <div className="form-error">{modalError}</div> : null}

              <div className="requirements-form-section">
                <h3>Thông tin dịch vụ</h3>
                <div className="requirements-form-grid">
                  <label className="document-type-field">
                    <span>Mã dịch vụ</span>
                    <input
                      value={form.serviceCode}
                      onChange={(event) => setForm((current) => ({ ...current, serviceCode: event.target.value.toUpperCase() }))}
                      placeholder="VD: SAO_Y"
                      required
                    />
                  </label>

                  <label className="document-type-field">
                    <span>Tên dịch vụ</span>
                    <input
                      value={form.serviceName}
                      onChange={(event) => setForm((current) => ({ ...current, serviceName: event.target.value }))}
                      placeholder="VD: Sao y công chứng"
                      required
                    />
                  </label>

                  <label className="document-type-field">
                    <span>Mức phí cơ bản</span>
                    <input
                      type="number"
                      min="0"
                      step="1000"
                      value={form.basePrice}
                      onChange={(event) => setForm((current) => ({ ...current, basePrice: Number(event.target.value) }))}
                      required
                    />
                  </label>

                  {editingServiceId ? (
                    <div className={`requirements-state-card ${form.isActive ? 'active' : ''}`}>
                      <span>Trạng thái</span>
                      <strong>{form.isActive ? 'Đang áp dụng' : 'Ngừng áp dụng'}</strong>
                    </div>
                  ) : null}
                </div>

                <label className="document-type-field">
                  <span>Mô tả</span>
                  <textarea
                    value={form.description}
                    onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
                    rows={3}
                    placeholder="Mô tả ngắn về dịch vụ công chứng này."
                  />
                </label>

                <label className={`requirements-doc-option ${form.requiresTemplate ? 'selected' : ''}`}>
                  <input
                    type="checkbox"
                    checked={form.requiresTemplate}
                    onChange={(event) => setForm((current) => ({ ...current, requiresTemplate: event.target.checked }))}
                  />
                  <span>
                    <strong>Yêu cầu mẫu văn bản</strong>
                    <small>Bật cho hợp đồng/ủy quyền/di chúc cần trình chiếu và ký; tắt cho chứng thực bản sao hoặc xác nhận chữ ký.</small>
                  </span>
                </label>
              </div>

              <div className="requirements-form-section">
                <div className="requirements-modal-toolbar">
                  <h3>Giấy tờ bắt buộc</h3>
                  <span>Đã chọn {form.requiredDocTypes.length} giấy tờ</span>
                </div>

                <div className="requirements-doc-list">
                  {configurableDocTypes.map((docType) => {
                    const checked = form.requiredDocTypes.includes(docType.code);
                    return (
                      <label key={docType.code} className={`requirements-doc-option ${checked ? 'selected' : ''}`}>
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleDocType(docType.code)}
                        />
                        <span>
                          <strong>{docType.name || getDocTypeName(documentTypes, docType.code)}</strong>
                          <small>{docType.code} · {getSourceLabel(docType.source)}</small>
                        </span>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div className="requirements-modal-actions">
                <button type="button" className="secondary-btn" onClick={closeModal} disabled={saving}>Hủy</button>
                <button type="submit" className="primary-btn" disabled={saving}>
                  {saving ? 'Đang lưu...' : 'Lưu cấu hình'}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}

      {confirmStatusConfig ? (
        <div
          className="requirements-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setConfirmStatusConfig(null);
          }}
        >
          <section className="requirements-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="service-status-title">
            <div>
              <h2 id="service-status-title">{confirmStatusConfig.isActive ? 'Vô hiệu hóa dịch vụ' : 'Kích hoạt lại dịch vụ'}</h2>
              <p>
                Bạn có chắc chắn muốn {confirmStatusConfig.isActive ? 'vô hiệu hóa' : 'kích hoạt lại'} dịch vụ{' '}
                <strong>{confirmStatusConfig.serviceName}</strong>?
              </p>
            </div>
            <div className="requirements-confirm-actions">
              <button type="button" className="secondary-btn" onClick={() => setConfirmStatusConfig(null)}>
                Hủy
              </button>
              <button
                type="button"
                className={`primary-btn ${confirmStatusConfig.isActive ? 'danger' : ''}`}
                onClick={() => void handleStatusChange()}
              >
                {confirmStatusConfig.isActive ? 'Vô hiệu hóa' : 'Kích hoạt lại'}
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </DashboardLayout>
  );
}
