import type { ContractType, DocType, RequestStatus, ServiceType } from '../types/request';

export const serviceTypeLabels: Record<string, string> = {
  ONLINE: 'Trực tuyến',
  OFFLINE: 'Trực tiếp',
};

export const contractTypeLabels: Record<string, string> = {
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

export const docTypeLabels: Record<string, string> = {
  ID_CARD: 'Giấy tờ tùy thân',
  REPRESENTATIVE_PROOF: 'Giấy tờ chứng minh đại diện',
  DRAFT_CONTRACT: 'Bản dự thảo hợp đồng',
  PROPERTY_PAPER: 'Giấy tờ tài sản',
  MARITAL_STATUS_PROOF: 'Giấy tờ tình trạng hôn nhân',
  RESIDENCE_PROOF: 'Giấy tờ cư trú',
  BUSINESS_REGISTRATION: 'Đăng ký kinh doanh',
  AUTHORIZATION_DOCUMENT: 'Văn bản ủy quyền',
  INHERITANCE_DOCUMENT: 'Giấy tờ thừa kế',
  PERSONAL_COMMITMENT_DOCUMENT: 'Văn bản cam kết cá nhân',
  SOURCE_DOCUMENT: 'Tài liệu gốc cần chứng thực',
  SIGNATURE_DOCUMENT: 'Văn bản cần xác nhận chữ ký',
  CIVIL_AGREEMENT_DOCUMENT: 'Văn bản thỏa thuận dân sự',
  OTHER_RELATED_DOCUMENT: 'Tài liệu liên quan khác',
  SIGNED_DOCUMENT: 'Tài liệu đã ký',
  SESSION_VIDEO: 'Video phiên họp',
  EVIDENCE_PHOTO: 'Ảnh bằng chứng đối chiếu',
};

export const requestStatusLabels: Record<string, { label: string; tone: string }> = {
  NEW: { label: 'Mới tạo', tone: 'badge-gray' },
  PROCESSING: { label: 'Chờ tiếp nhận', tone: 'badge-yellow' },
  ACCEPTED: { label: 'Đã tiếp nhận', tone: 'badge-indigo' },
  SCHEDULED: { label: 'Đã lên lịch', tone: 'badge-purple' },
  IN_VIDEO_CALL: { label: 'Đang xác thực danh tính (Video)', tone: 'badge-orange' },
  AWAITING_PAYMENT: { label: 'Chờ thanh toán', tone: 'badge-yellow' },
  COMPLETED: { label: 'Hoàn thành', tone: 'badge-green' },
  CANCELLED: { label: 'Đã hủy', tone: 'badge-gray' },
  REJECTED: { label: 'Bị từ chối', tone: 'badge-red' },
};

export function toContractTypeLabel(value: ContractType | string | null | undefined) {
  if (!value) return '';
  return contractTypeLabels[value] ?? value;
}

export function toServiceTypeLabel(value: ServiceType | string | null | undefined) {
  if (!value) return '';
  return serviceTypeLabels[value] ?? value;
}

export function toDocTypeLabel(value: DocType | string | null | undefined) {
  if (!value) return '';
  return docTypeLabels[value] ?? value;
}

export function toRequestStatusMeta(value: RequestStatus | string | null | undefined) {
  if (!value) return { label: '', tone: 'badge-gray' };
  return requestStatusLabels[value] ?? { label: String(value), tone: 'badge-gray' };
}
