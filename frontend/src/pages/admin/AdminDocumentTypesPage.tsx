import { FormEvent, useEffect, useMemo, useState } from 'react';
import { DashboardLayout } from '../../components/DashboardLayout';
import {
  createDocumentTypeApi,
  listDocumentTypesApi,
  updateDocumentTypeApi,
} from '../../features/admin/adminApi';
import { toApiErrorMessage } from '../../lib/apiError';
import type { DocumentType, DocumentTypeRequest } from '../../types/admin';

const emptyForm: DocumentTypeRequest = {
  code: '',
  name: '',
  description: '',
  source: 'USER_UPLOAD',
  allowedFileGroup: 'DOCUMENT',
  isActive: true,
  sortOrder: 0,
};

const sourceLabels: Record<string, string> = {
  USER_UPLOAD: 'Người dân tải lên',
  SYSTEM_GENERATED: 'Hệ thống tạo',
  INTERNAL: 'Nội bộ',
};

const fileGroupLabels: Record<string, string> = {
  DOCUMENT: 'Tài liệu',
  IMAGE: 'Hình ảnh',
  VIDEO: 'Video',
  ANY: 'Bất kỳ',
};

function toFormValue(item: DocumentType): DocumentTypeRequest {
  return {
    code: item.code,
    name: item.name,
    description: item.description || '',
    source: item.source,
    allowedFileGroup: item.allowedFileGroup,
    isActive: item.isActive,
    sortOrder: item.sortOrder ?? 0,
  };
}

function SourceIcon({ source }: { source: string }) {
  if (source === 'SYSTEM_GENERATED') {
    return (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d="M7 9h10M7 13h6" />
      </svg>
    );
  }

  if (source === 'INTERNAL') {
    return (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M12 3l8 4v5c0 5-3.4 8.5-8 9-4.6-.5-8-4-8-9V7l8-4z" />
      </svg>
    );
  }

  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <path d="M14 2v6h6M12 18v-6M9 15h6" />
    </svg>
  );
}

function getSourceTone(source: string) {
  if (source === 'SYSTEM_GENERATED') return 'system';
  if (source === 'INTERNAL') return 'internal';
  return 'upload';
}

function compareDocumentTypes(a: DocumentType, b: DocumentType) {
  return a.name.localeCompare(b.name, 'vi') || a.code.localeCompare(b.code);
}

export function AdminDocumentTypesPage() {
  const [items, setItems] = useState<DocumentType[]>([]);
  const [form, setForm] = useState<DocumentTypeRequest>(emptyForm);
  const [editingCode, setEditingCode] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [pageError, setPageError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  const editingItem = useMemo(
    () => items.find((item) => item.code === editingCode) || null,
    [editingCode, items],
  );

  const stats = useMemo(() => ({
    total: items.length,
    active: items.filter((item) => item.isActive).length,
    userUpload: items.filter((item) => item.source === 'USER_UPLOAD').length,
    system: items.filter((item) => item.source === 'SYSTEM_GENERATED').length,
  }), [items]);

  const filteredItems = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    const next = keyword
      ? items.filter((item) =>
        [item.code, item.name, item.description, sourceLabels[item.source], fileGroupLabels[item.allowedFileGroup]]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(keyword)),
      )
      : [...items];
    return next.sort(compareDocumentTypes);
  }, [items, query]);

  const loadItems = async () => {
    setLoading(true);
    setPageError('');
    try {
      const data = await listDocumentTypesApi();
      setItems(data);
    } catch (error) {
      setPageError(toApiErrorMessage(error, 'Không tải được danh mục giấy tờ'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadItems();
  }, []);

  const openCreateModal = () => {
    setForm(emptyForm);
    setEditingCode(null);
    setPageError('');
    setSuccessMessage('');
    setModalOpen(true);
  };

  const openEditModal = (item: DocumentType) => {
    setForm(toFormValue(item));
    setEditingCode(item.code);
    setPageError('');
    setSuccessMessage('');
    setModalOpen(true);
  };

  const closeModal = () => {
    if (saving) return;
    setModalOpen(false);
    setEditingCode(null);
    setForm(emptyForm);
    setPageError('');
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    setPageError('');
    setSuccessMessage('');
    const payload = { ...form, code: form.code.trim().toUpperCase(), name: form.name.trim() };

    try {
      const saved = editingCode
        ? await updateDocumentTypeApi(editingCode, payload)
        : await createDocumentTypeApi(payload);
      setItems((current) => {
        const exists = current.some((item) => item.code === saved.code);
        const next = exists
          ? current.map((item) => (item.code === saved.code ? saved : item))
          : [...current, saved];
        return next.sort(compareDocumentTypes);
      });
      setSuccessMessage(editingCode ? 'Đã cập nhật loại giấy tờ.' : 'Đã thêm loại giấy tờ.');
      setModalOpen(false);
      setEditingCode(null);
      setForm(emptyForm);
    } catch (error) {
      setPageError(toApiErrorMessage(error, 'Không lưu được loại giấy tờ'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <DashboardLayout role="admin">
      <div className="page-content admin-document-types-page">
        <div className="page-header with-action document-types-hero">
          <div>
            <h1>Danh mục giấy tờ</h1>
            <p>Quản lý tên hiển thị, nguồn phát sinh và nhóm file của từng loại giấy tờ.</p>
          </div>
          <button type="button" className="primary-btn" onClick={openCreateModal}>Thêm loại giấy tờ</button>
        </div>

        {pageError && !modalOpen ? <div className="form-error">{pageError}</div> : null}
        {successMessage ? <div className="inline-success">{successMessage}</div> : null}

        <div className="document-type-stats">
          {[
            ['Tổng loại giấy tờ', stats.total],
            ['Đang sử dụng', stats.active],
            ['Người dân tải lên', stats.userUpload],
            ['Hệ thống tạo', stats.system],
          ].map(([label, value]) => (
            <div key={label} className="document-type-stat">
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </div>

        <section className="document-type-panel">
          <div className="document-type-toolbar">
            <div>
              <h2>Các loại giấy tờ</h2>
              <p>{filteredItems.length} mục hiển thị</p>
            </div>
            <label className="document-type-search">
              <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="11" cy="11" r="8" />
                <path d="M21 21l-4.35-4.35" />
              </svg>
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Tìm theo mã, tên, mô tả..."
              />
            </label>
          </div>

          {loading ? (
            <p className="document-type-empty">Đang tải danh mục...</p>
          ) : filteredItems.length === 0 ? (
            <p className="document-type-empty">Không có loại giấy tờ phù hợp.</p>
          ) : (
            <div className="document-type-table-wrap">
              <table className="document-type-table">
                <colgroup>
                  <col className="document-type-col-name" />
                  <col className="document-type-col-source" />
                  <col className="document-type-col-file-group" />
                  <col className="document-type-col-status" />
                  <col className="document-type-col-actions" />
                </colgroup>
                <thead>
                  <tr>
                    <th>Loại giấy tờ</th>
                    <th>Nguồn</th>
                    <th>Nhóm file</th>
                    <th>Trạng thái</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {filteredItems.map((item) => {
                    const tone = getSourceTone(item.source);
                    return (
                      <tr key={item.code}>
                        <td>
                          <div className="document-type-name-cell">
                            <div className={`document-type-icon ${tone}`}>
                              <SourceIcon source={item.source} />
                            </div>
                            <div className="document-type-title-block">
                              <strong>{item.name}</strong>
                              <span className="document-type-code">{item.code}</span>
                              {item.description ? <p>{item.description}</p> : null}
                            </div>
                          </div>
                        </td>
                        <td><span className="document-type-plain-pill">{sourceLabels[item.source] || item.source}</span></td>
                        <td><span className="document-type-plain-pill">{fileGroupLabels[item.allowedFileGroup] || item.allowedFileGroup}</span></td>
                        <td>
                          <div className="document-type-statuses">
                            <span className={`status-badge ${item.isActive ? 'badge-green' : 'badge-gray'}`}>
                              {item.isActive ? 'Đang dùng' : 'Tạm tắt'}
                            </span>
                          </div>
                        </td>
                        <td className="document-type-actions">
                          <button type="button" className="ghost-btn" onClick={() => openEditModal(item)}>
                            Sửa
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
          className="document-type-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) closeModal();
          }}
        >
          <section className="document-type-modal" role="dialog" aria-modal="true" aria-labelledby="document-type-modal-title">
            <div className="document-type-modal-head">
              <div>
                <h2 id="document-type-modal-title">
                  {editingCode ? 'Cập nhật loại giấy tờ' : 'Thêm loại giấy tờ'}
                </h2>
                <p>{editingCode ? editingCode : 'Tạo loại giấy tờ mới để dùng trong cấu hình hồ sơ.'}</p>
              </div>
              <button type="button" className="ghost-btn" onClick={closeModal} disabled={saving}>Đóng</button>
            </div>

            <form className="document-type-form" onSubmit={handleSubmit}>
              {pageError ? <div className="form-error">{pageError}</div> : null}

              <label className="document-type-field">
                <span>Mã loại giấy tờ</span>
                <input
                  value={form.code}
                  onChange={(event) => setForm((current) => ({ ...current, code: event.target.value.toUpperCase() }))}
                  placeholder="VD: LAND_USE_CERTIFICATE"
                  disabled={!!editingCode}
                  required
                />
              </label>

              <label className="document-type-field">
                <span>Tên hiển thị cho người dùng</span>
                <input
                  value={form.name}
                  onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))}
                  placeholder="VD: Giấy chứng nhận quyền sử dụng đất"
                  required
                />
              </label>

              <label className="document-type-field">
                <span>Mô tả nghiệp vụ</span>
                <textarea
                  value={form.description}
                  onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))}
                  placeholder="Nhập mô tả ngắn gọn về loại giấy tờ này để phân biệt với các loại khác, không bắt buộc nhưng rất hữu ích khi có nhiều loại giấy tờ."
                  rows={3}
                />
              </label>

              <div className="document-type-form-row">
                <label className="document-type-field">
                  <span>Nguồn tài liệu</span>
                  <select
                    value={form.source}
                    onChange={(event) => setForm((current) => ({ ...current, source: event.target.value }))}
                    disabled={!!editingItem?.isSystem}
                  >
                    <option value="USER_UPLOAD">Người dân tải lên</option>
                    <option value="SYSTEM_GENERATED">Hệ thống tạo</option>
                  </select>
                </label>

                <label className="document-type-field">
                  <span>Nhóm file</span>
                  <select
                    value={form.allowedFileGroup}
                    onChange={(event) => setForm((current) => ({ ...current, allowedFileGroup: event.target.value }))}
                    disabled={!!editingItem?.isSystem}
                  >
                    <option value="DOCUMENT">Tài liệu</option>
                    <option value="IMAGE">Hình ảnh</option>
                    <option value="VIDEO">Video</option>
                    <option value="ANY">Bất kỳ</option>
                  </select>
                </label>
              </div>

              <label className="document-type-toggle">
                <input
                  type="checkbox"
                  checked={form.isActive}
                  onChange={(event) => setForm((current) => ({ ...current, isActive: event.target.checked }))}
                />
                <span>Đang sử dụng</span>
              </label>

              {editingItem?.isSystem ? (
                <div className="inline-warning">Loại giấy tờ hệ thống chỉ nên chỉnh tên hiển thị, mô tả hoặc trạng thái.</div>
              ) : null}

              <div className="document-type-modal-actions">
                <button type="button" className="secondary-btn" onClick={closeModal} disabled={saving}>Hủy</button>
                <button type="submit" className="primary-btn" disabled={saving}>
                  {saving ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </DashboardLayout>
  );
}
