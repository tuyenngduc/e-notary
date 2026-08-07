import { useEffect, useState } from 'react';
import { DashboardLayout } from '../../components/DashboardLayout';
import {
  createOfficeApi,
  deleteOfficeApi,
  listOfficesApi,
  updateOfficeApi,
} from '../../features/admin/adminApi';
import { toApiErrorMessage } from '../../lib/apiError';
import type { NotaryOffice, NotaryOfficeRequest } from '../../types/admin';

const initialOfficeForm: NotaryOfficeRequest = {
  name: '',
  address: '',
  phoneNumber: '',
  workingHours: '',
  isActive: true,
};

export function AdminOfficesPage() {
  const [offices, setOffices] = useState<NotaryOffice[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [pageError, setPageError] = useState('');
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<NotaryOfficeRequest>(initialOfficeForm);
  const [formError, setFormError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const loadOffices = async () => {
    setIsLoading(true);
    try {
      setOffices(await listOfficesApi());
      setPageError('');
    } catch (error) {
      setPageError(toApiErrorMessage(error, 'Không tải được danh sách văn phòng công chứng'));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadOffices();
  }, []);

  const openCreateModal = () => {
    setEditingId(null);
    setForm(initialOfficeForm);
    setFormError('');
    setShowModal(true);
  };

  const openEditModal = (office: NotaryOffice) => {
    setEditingId(office.id);
    setForm({
      name: office.name,
      address: office.address,
      phoneNumber: office.phoneNumber || '',
      workingHours: office.workingHours || '',
      isActive: office.isActive,
    });
    setFormError('');
    setShowModal(true);
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm('Bạn có chắc chắn muốn ngừng áp dụng văn phòng này?')) return;
    try {
      await deleteOfficeApi(id);
      await loadOffices();
    } catch (error) {
      setPageError(toApiErrorMessage(error, 'Không thể ngừng áp dụng văn phòng'));
    }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setFormError('');

    if (!form.name.trim() || !form.address.trim()) {
      setFormError('Vui lòng nhập tên và địa chỉ văn phòng');
      return;
    }

    setIsSubmitting(true);
    try {
      if (editingId) {
        await updateOfficeApi(editingId, form);
      } else {
        await createOfficeApi(form);
      }
      setShowModal(false);
      await loadOffices();
    } catch (error) {
      setFormError(toApiErrorMessage(error, 'Lưu thông tin văn phòng thất bại'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <DashboardLayout role="admin">
      <div className="page-content" style={{ maxWidth: '1200px', margin: '0 auto', padding: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem', marginBottom: '2rem' }}>
          <div>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.5rem' }}>Văn phòng công chứng</h1>
            <p className="muted-text">Quản lý các địa chỉ công chứng trực tiếp để công chứng viên chọn khi lên lịch gặp mặt.</p>
          </div>
          <button type="button" className="primary-btn" onClick={openCreateModal} style={{ whiteSpace: 'nowrap' }}>
            + Thêm văn phòng
          </button>
        </div>

        {pageError ? (
          <div className="form-error" style={{ marginBottom: '1.5rem', padding: '1rem', backgroundColor: '#fee2e2', color: '#b91c1c', borderRadius: '8px' }}>
            {pageError}
          </div>
        ) : null}

        <div className="soft-card" style={{ padding: 0, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead style={{ backgroundColor: 'rgba(0,0,0,0.02)', borderBottom: '1px solid var(--border-color)' }}>
                <tr>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)' }}>Tên văn phòng</th>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)' }}>Địa chỉ</th>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)' }}>Liên hệ</th>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)' }}>Trạng thái</th>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)', textAlign: 'right' }}>Hành động</th>
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  <tr>
                    <td colSpan={5} style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                      Đang tải danh sách...
                    </td>
                  </tr>
                ) : offices.length === 0 ? (
                  <tr>
                    <td colSpan={5} style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                      Chưa có văn phòng công chứng nào.
                    </td>
                  </tr>
                ) : (
                  offices.map((office) => (
                    <tr key={office.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                      <td style={{ padding: '1rem', fontWeight: 600 }}>{office.name}</td>
                      <td style={{ padding: '1rem', minWidth: '280px' }}>{office.address}</td>
                      <td style={{ padding: '1rem' }}>
                        <div>{office.phoneNumber || 'Chưa cập nhật'}</div>
                        {office.workingHours ? (
                          <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>{office.workingHours}</div>
                        ) : null}
                      </td>
                      <td style={{ padding: '1rem' }}>
                        {office.isActive ? (
                          <span className="status-badge badge-green">Đang áp dụng</span>
                        ) : (
                          <span className="status-badge badge-gray">Ngừng áp dụng</span>
                        )}
                      </td>
                      <td style={{ padding: '1rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <button
                          type="button"
                          className="ghost-btn"
                          style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem', marginRight: '0.5rem', color: 'var(--primary-color)' }}
                          onClick={() => openEditModal(office)}
                        >
                          Sửa
                        </button>
                        <button
                          type="button"
                          className="ghost-btn"
                          style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}
                          onClick={() => void handleDelete(office.id)}
                        >
                          Ngừng áp dụng
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {showModal ? (
          <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem', backdropFilter: 'blur(4px)' }}>
            <div className="soft-card" style={{ width: '100%', maxWidth: '560px', padding: '2rem', position: 'relative' }}>
              <button
                type="button"
                onClick={() => setShowModal(false)}
                style={{ position: 'absolute', top: '1.5rem', right: '1.5rem', background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: 'var(--text-muted)' }}
              >
                &times;
              </button>
              <h2 style={{ marginBottom: '1.5rem', fontSize: '1.5rem' }}>
                {editingId ? 'Cập nhật văn phòng' : 'Thêm văn phòng mới'}
              </h2>

              <form className="form-stack" onSubmit={(event) => void handleSubmit(event)} noValidate>
                <label className="field">
                  <span>Tên văn phòng *</span>
                  <input
                    type="text"
                    value={form.name}
                    onChange={(event) => setForm({ ...form, name: event.target.value })}
                    required
                  />
                </label>

                <label className="field">
                  <span>Địa chỉ gặp mặt *</span>
                  <textarea
                    value={form.address}
                    onChange={(event) => setForm({ ...form, address: event.target.value })}
                    rows={3}
                    required
                  />
                </label>

                <label className="field">
                  <span>Số điện thoại</span>
                  <input
                    type="text"
                    value={form.phoneNumber}
                    onChange={(event) => setForm({ ...form, phoneNumber: event.target.value })}
                  />
                </label>

                <label className="field">
                  <span>Giờ làm việc</span>
                  <input
                    type="text"
                    value={form.workingHours}
                    onChange={(event) => setForm({ ...form, workingHours: event.target.value })}
                    placeholder="Thứ 2 - Thứ 6, 08:00 - 17:00"
                  />
                </label>

                <label className="field checkbox-field" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', marginTop: '0.5rem' }}>
                  <input
                    type="checkbox"
                    checked={form.isActive}
                    onChange={(event) => setForm({ ...form, isActive: event.target.checked })}
                    style={{ width: 'auto' }}
                  />
                  <span>Cho phép công chứng viên chọn văn phòng này</span>
                </label>

                {formError ? <div className="form-error">{formError}</div> : null}

                <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                  <button type="button" className="ghost-btn" onClick={() => setShowModal(false)} style={{ flex: 1 }} disabled={isSubmitting}>
                    Hủy
                  </button>
                  <button type="submit" className="primary-btn" disabled={isSubmitting} style={{ flex: 1 }}>
                    {isSubmitting ? 'Đang lưu...' : 'Lưu lại'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        ) : null}
      </div>
    </DashboardLayout>
  );
}
