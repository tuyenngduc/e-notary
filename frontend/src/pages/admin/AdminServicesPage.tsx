import { useEffect, useState } from 'react';
import { DashboardLayout } from '../../components/DashboardLayout';
import { listServicesApi, createServiceApi, updateServiceApi, deleteServiceApi } from '../../features/admin/adminApi';
import { toApiErrorMessage } from '../../lib/apiError';
import type { NotaryServiceType, NotaryServiceTypeRequest } from '../../types/admin';

const initialFormState: NotaryServiceTypeRequest = {
  serviceCode: '',
  name: '',
  basePrice: 0,
  description: '',
  isActive: true,
  requiresTemplate: true,
};

export function AdminServicesPage() {
  const [services, setServices] = useState<NotaryServiceType[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  
  const [showModal, setShowModal] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<NotaryServiceTypeRequest>(initialFormState);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [pageError, setPageError] = useState('');

  const loadServices = async () => {
    setIsLoading(true);
    try {
      const data = await listServicesApi();
      setServices(data);
      setPageError('');
    } catch (error) {
      setPageError(toApiErrorMessage(error, 'Không tải được danh sách dịch vụ'));
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    void loadServices();
  }, []);

  const openCreateModal = () => {
    setEditingId(null);
    setForm(initialFormState);
    setErrorMsg('');
    setShowModal(true);
  };

  const openEditModal = (service: NotaryServiceType) => {
    setEditingId(service.id);
    setForm({
      serviceCode: service.serviceCode,
      name: service.name,
      basePrice: service.basePrice,
      description: service.description || '',
      isActive: service.isActive,
      requiresTemplate: service.requiresTemplate,
    });
    setErrorMsg('');
    setShowModal(true);
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm("Bạn có chắc chắn muốn vô hiệu hóa/xóa dịch vụ này?")) return;
    try {
      await deleteServiceApi(id);
      await loadServices();
      setPageError('');
    } catch (error) {
      setPageError(toApiErrorMessage(error, 'Lỗi khi xóa dịch vụ'));
    }
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');
    setIsSubmitting(true);
    
    try {
      if (editingId) {
        await updateServiceApi(editingId, form);
      } else {
        await createServiceApi(form);
      }
      setShowModal(false);
      await loadServices();
    } catch (error) {
      setErrorMsg(toApiErrorMessage(error, 'Lưu thông tin thất bại'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <DashboardLayout role="admin">
      <div className="page-content" style={{ maxWidth: '1200px', margin: '0 auto', padding: '2rem' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
          <div>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '0.5rem' }}>Bảng giá Dịch vụ</h1>
            <p className="muted-text">Quản lý danh mục dịch vụ công chứng và giá tiền.</p>
          </div>
          <button 
            className="primary-btn" 
            onClick={openCreateModal}
            style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}
          >
            <span>+</span> Thêm Dịch Vụ
          </button>
        </div>

        {pageError && (
          <div className="form-error" style={{ marginBottom: '1.5rem', padding: '1rem', backgroundColor: '#fee2e2', color: '#b91c1c', borderRadius: '8px' }}>
            {pageError}
          </div>
        )}

        {/* Services Table */}
        <div className="soft-card" style={{ padding: 0, overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead style={{ backgroundColor: 'rgba(0,0,0,0.02)', borderBottom: '1px solid var(--border-color)' }}>
                <tr>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)' }}>Mã dịch vụ</th>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)' }}>Tên dịch vụ</th>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)' }}>Mức phí</th>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)' }}>Trạng thái</th>
                  <th style={{ padding: '1rem', fontWeight: 600, color: 'var(--text-muted)', textAlign: 'right' }}>Hành động</th>
                </tr>
              </thead>
              <tbody>
                {isLoading ? (
                  <tr>
                    <td colSpan={5} style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>Đang tải danh sách...</td>
                  </tr>
                ) : services.length === 0 ? (
                  <tr>
                    <td colSpan={5} style={{ padding: '2rem', textAlign: 'center', color: 'var(--text-muted)' }}>Chưa có dịch vụ nào.</td>
                  </tr>
                ) : (
                  services.map((svc) => (
                    <tr key={svc.id} style={{ borderBottom: '1px solid var(--border-color)' }}>
                      <td style={{ padding: '1rem', fontWeight: 500 }}>{svc.serviceCode}</td>
                      <td style={{ padding: '1rem' }}>
                        <div>{svc.name}</div>
                        {svc.description && <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: '0.25rem' }}>{svc.description}</div>}
                      </td>
                      <td style={{ padding: '1rem', color: 'var(--primary-color)', fontWeight: 600 }}>
                        {svc.basePrice.toLocaleString('vi-VN')} ₫
                      </td>
                      <td style={{ padding: '1rem' }}>
                        {svc.isActive ? (
                          <span className="status-badge badge-green">Đang áp dụng</span>
                        ) : (
                          <span className="status-badge badge-gray">Ngừng cung cấp</span>
                        )}
                      </td>
                      <td style={{ padding: '1rem', textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <button 
                          className="ghost-btn" 
                          style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem', marginRight: '0.5rem', color: 'var(--primary-color)' }}
                          onClick={() => openEditModal(svc)}
                        >
                          Sửa
                        </button>
                        <button 
                          className="ghost-btn" 
                          style={{ padding: '0.4rem 0.8rem', fontSize: '0.85rem', color: 'var(--text-muted)' }}
                          onClick={() => handleDelete(svc.id)}
                        >
                          Xóa
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Modal */}
        {showModal && (
          <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, padding: '1rem', backdropFilter: 'blur(4px)' }}>
            <div className="soft-card" style={{ width: '100%', maxWidth: '500px', padding: '2rem', position: 'relative', animation: 'fadeIn 0.2s ease-out' }}>
              <button 
                type="button"
                onClick={() => setShowModal(false)}
                style={{ position: 'absolute', top: '1.5rem', right: '1.5rem', background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: 'var(--text-muted)' }}
              >
                &times;
              </button>
              <h2 style={{ marginBottom: '1.5rem', fontSize: '1.5rem' }}>
                {editingId ? 'Cập nhật Dịch vụ' : 'Thêm Dịch Vụ Mới'}
              </h2>
              
              <form className="form-stack" onSubmit={onSubmit} noValidate>
                <label className="field">
                  <span>Mã dịch vụ (VD: SAO_Y)</span>
                  <input
                    type="text"
                    value={form.serviceCode}
                    onChange={(e) => setForm({ ...form, serviceCode: e.target.value })}
                    required
                  />
                </label>

                <label className="field">
                  <span>Tên dịch vụ</span>
                  <input
                    type="text"
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    required
                  />
                </label>

                <label className="field">
                  <span>Mức phí cơ bản (VNĐ)</span>
                  <input
                    type="number"
                    min="0"
                    step="1000"
                    value={form.basePrice}
                    onChange={(e) => setForm({ ...form, basePrice: Number(e.target.value) })}
                    required
                  />
                </label>

                <label className="field">
                  <span>Mô tả ngắn gọn</span>
                  <textarea
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                    rows={3}
                  />
                </label>

                <label className="field checkbox-field" style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer', marginTop: '0.5rem' }}>
                  <input
                    type="checkbox"
                    checked={form.isActive}
                    onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                    style={{ width: 'auto' }}
                  />
                  <span>Áp dụng dịch vụ này (Đang hoạt động)</span>
                </label>

                {errorMsg && <div className="form-error">{errorMsg}</div>}

                <div style={{ display: 'flex', gap: '1rem', marginTop: '1rem' }}>
                  <button type="button" className="ghost-btn" onClick={() => setShowModal(false)} style={{ flex: 1 }}>
                    Hủy
                  </button>
                  <button type="submit" className="primary-btn" disabled={isSubmitting} style={{ flex: 1 }}>
                    {isSubmitting ? 'Đang lưu...' : 'Lưu lại'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
}
