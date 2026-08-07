import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { DashboardLayout } from '../../components/DashboardLayout';
import {
  createRequestApi,
} from '../../features/requests/requestApi';
import { listActiveServicesApi } from '../../features/services/serviceApi';
import { toApiErrorMessage } from '../../lib/apiError';
import type { NotaryServiceType } from '../../types/admin';
import type { ContractType, ServiceType } from '../../types/request';

const contractOptions: Array<{ value: ContractType; label: string }> = [
  { value: 'POWER_OF_ATTORNEY', label: 'Giấy ủy quyền' },
  { value: 'PERSONAL_COMMITMENT', label: 'Văn bản cam kết cá nhân' },
  { value: 'SIGNATURE_CERTIFICATION', label: 'Xác nhận chữ ký' },
  { value: 'E_COPY_CERTIFICATION', label: 'Chứng thực bản sao' },
  { value: 'WILL', label: 'Di chúc' },
  { value: 'LOAN_AGREEMENT', label: 'Hợp đồng vay mượn' },
  { value: 'CIVIL_AGREEMENT', label: 'Thỏa thuận dân sự' },
];

function formatPrice(value: number) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value);
}

export function NewRequestPage() {
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [services, setServices] = useState<NotaryServiceType[]>([]);
  const [servicesLoading, setServicesLoading] = useState(true);
  const [servicesError, setServicesError] = useState('');
  const [form, setForm] = useState<{
    serviceType: ServiceType;
    contractType: ContractType;
    description: string;
  }>({
    serviceType: 'ONLINE',
    contractType: 'POWER_OF_ATTORNEY',
    description: '',
  });

  useEffect(() => {
    const loadServices = async () => {
      setServicesLoading(true);
      setServicesError('');
      try {
        setServices(await listActiveServicesApi());
      } catch (error) {
        setServicesError(toApiErrorMessage(error, 'Không tải được bảng giá dịch vụ'));
      } finally {
        setServicesLoading(false);
      }
    };

    void loadServices();
  }, []);

  const selectedService = useMemo(
    () => services.find((item) => item.serviceCode === form.contractType),
    [form.contractType, services],
  );

  const serviceOptions = useMemo(
    () => services.length > 0
      ? services.map((service) => ({
          value: service.serviceCode as ContractType,
          label: service.name,
        }))
      : contractOptions,
    [services],
  );

  useEffect(() => {
    if (services.length === 0) {
      return;
    }
    if (!services.some((service) => service.serviceCode === form.contractType)) {
      setForm((prev) => ({ ...prev, contractType: services[0].serviceCode as ContractType }));
    }
  }, [form.contractType, services]);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitError('');

    setSubmitting(true);
    try {
      const created = await createRequestApi({
        serviceType: form.serviceType,
        contractType: form.contractType,
        description: form.description,
      });

      navigate(`/customer/request/${created.requestId}`);
    } catch (error) {
      setSubmitError(toApiErrorMessage(error, 'Không thể tạo yêu cầu'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <DashboardLayout role="customer">
      <div className="page-content narrow-content">
        <div className="page-header">
          <h1>Yêu cầu công chứng mới</h1>
          <p>Điền thông tin để tạo yêu cầu công chứng trực tuyến hoặc trực tiếp.</p>
        </div>

        <section className="soft-card">
          <form className="form-stack" onSubmit={handleSubmit}>
            <div className="field">
              <span>Loại dịch vụ</span>
              <div className="service-type-options" role="radiogroup" aria-label="Loại dịch vụ">
                <label className={`service-type-option ${form.serviceType === 'ONLINE' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="serviceType"
                    value="ONLINE"
                    checked={form.serviceType === 'ONLINE'}
                    onChange={() => setForm((prev) => ({ ...prev, serviceType: 'ONLINE' }))}
                  />
                  <span className="service-type-option-title">Trực tuyến</span>
                  <small>Thực hiện trực tuyến trên hệ thống.</small>
                </label>
                <label className={`service-type-option ${form.serviceType === 'OFFLINE' ? 'selected' : ''}`}>
                  <input
                    type="radio"
                    name="serviceType"
                    value="OFFLINE"
                    checked={form.serviceType === 'OFFLINE'}
                    onChange={() => setForm((prev) => ({ ...prev, serviceType: 'OFFLINE' }))}
                  />
                  <span className="service-type-option-title">Trực tiếp</span>
                  <small>Thực hiện trực tiếp tại các văn phòng công chứng.</small>
                </label>
              </div>
            </div>

            <label className="field">
              <span>Loại hợp đồng</span>
              <select
                value={form.contractType}
                onChange={(event) =>
                  setForm((prev) => ({ ...prev, contractType: event.target.value as ContractType }))
                }
              >
                {serviceOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Mô tả chi tiết</span>
              <textarea
                rows={4}
                value={form.description}
                onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))}
                placeholder="Nhập thông tin bổ sung cho công chứng viên"
              />
            </label>

            <div className="price-panel">
              <strong>Giá dự kiến:</strong>{' '}
              {servicesLoading
                ? 'Đang tải bảng giá...'
                : selectedService
                  ? formatPrice(selectedService.basePrice)
                  : 'Theo báo giá'}
            </div>

            {servicesError ? <div className="form-error">{servicesError}</div> : null}

            {submitError ? <div className="form-error">{submitError}</div> : null}

            <div className="action-row">
              <button className="primary-btn" type="submit" disabled={submitting}>
                {submitting ? 'Đang tạo...' : 'Tạo yêu cầu'}
              </button>
              <button className="ghost-btn" type="button" onClick={() => navigate(-1)} disabled={submitting}>
                Hủy
              </button>
            </div>
          </form>
        </section>
      </div>
    </DashboardLayout>
  );
}

