import type { ContractTemplate } from './admin';

export type RequestStatus =
  | 'NEW'
  | 'PROCESSING'
  | 'ACCEPTED'
  | 'SCHEDULED'
  | 'IN_VIDEO_CALL'
  | 'AWAITING_PAYMENT'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'REJECTED'
  | string;

export type ServiceType = 'ONLINE' | 'OFFLINE';

export type ContractType =
  | 'TRANSFER_OF_PROPERTY'
  | 'POWER_OF_ATTORNEY'
  | 'PERSONAL_COMMITMENT'
  | 'SIGNATURE_CERTIFICATION'
  | 'E_COPY_CERTIFICATION'
  | 'LOAN_AGREEMENT'
  | 'WILL'
  | 'CIVIL_AGREEMENT'
  | 'MARRIAGE_CONTRACT'
  | 'BUSINESS_CONTRACT'
  | 'OTHER';

export type DocType = string;

export interface RequiredDocumentItem {
  code: DocType;
  name: string;
  source: 'USER_UPLOAD' | 'SYSTEM_GENERATED' | 'INTERNAL' | string;
  allowedFileGroup: 'DOCUMENT' | 'IMAGE' | 'VIDEO' | 'ANY' | string;
  uploaded: boolean;
  missing: boolean;
}

export interface DocumentRequirementResponse {
  requiredDocTypes: DocType[];
  uploadedDocTypes: DocType[];
  missingDocTypes: DocType[];
  requiredDocuments?: RequiredDocumentItem[];
  readyForAccept: boolean;
}

export interface NotaryRequest {
  requestId: string;
  clientId: string | null;
  notaryId: string | null;
  serviceType: ServiceType;
  contractType: ContractType;
  requiresTemplate: boolean;
  description: string;
  status: RequestStatus;
  rejectionReason: string | null;
  meetingUrl: string | null;
  createdAt: string;
  updatedAt: string;
  documentIds: string[];
  documentRequirements?: DocumentRequirementResponse;
  selectedTemplate?: ContractTemplate | null;
  appointment?: Appointment | null;
}

export interface DocumentItem {
  documentId: string;
  requestId: string;
  filePath: string;
  originalFileName?: string | null;
  displayName?: string | null;
  contentType?: string | null;
  fileSize?: number | null;
  absolutePath: string | null;
  docType: DocType;
  fileHash: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRequestPayload {
  serviceType: ServiceType;
  contractType: ContractType;
  description: string;
}

export interface PagedData<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export type AppointmentStatus = 'PENDING' | 'FINISHED' | 'CANCELLED';

export interface Appointment {
  appointmentId: string;
  requestId: string;
  serviceType: ServiceType;
  contractType: ContractType;
  scheduledTime: string;
  meetingUrl: string | null;
  physicalAddress: string | null;
  status: AppointmentStatus;
  createdAt: string;
  clientName: string | null;
  notaryName: string | null;
  selectedTemplate?: ContractTemplate | null;
  requiresTemplate?: boolean;
}
