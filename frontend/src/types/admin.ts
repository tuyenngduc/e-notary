export interface NotaryServiceType {
  id: string;
  serviceCode: string;
  name: string;
  basePrice: number;
  description: string;
  isActive: boolean;
  requiresTemplate: boolean;
}

export interface NotaryServiceTypeRequest {
  serviceCode: string;
  name: string;
  basePrice: number;
  description: string;
  isActive: boolean;
  requiresTemplate: boolean;
}

export interface DocumentRequirementConfig {
  serviceId: string;
  serviceCode: string;
  serviceName: string;
  description: string;
  basePrice: number;
  isActive: boolean;
  requiresTemplate: boolean;
  requiredDocTypes: string[];
}

export interface DocumentRequirementConfigRequest {
  serviceCode: string;
  serviceName: string;
  description: string;
  basePrice: number;
  isActive: boolean;
  requiresTemplate: boolean;
  requiredDocTypes: string[];
}

export interface DocumentType {
  code: string;
  name: string;
  description: string | null;
  source: 'USER_UPLOAD' | 'SYSTEM_GENERATED' | 'INTERNAL' | string;
  allowedFileGroup: 'DOCUMENT' | 'IMAGE' | 'VIDEO' | 'ANY' | string;
  isActive: boolean;
  isSystem: boolean;
  sortOrder: number;
}

export interface DocumentTypeRequest {
  code: string;
  name: string;
  description: string;
  source: string;
  allowedFileGroup: string;
  isActive: boolean;
  sortOrder: number;
}

export interface ContractTemplate {
  id: string;
  serviceTypeId: string;
  serviceTypeCode: string;
  name: string;
  fileUrl: string;
  version?: string;
  isActive: boolean;
  updatedAt: string;
}

export interface NotaryOffice {
  id: string;
  name: string;
  address: string;
  phoneNumber: string | null;
  workingHours: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface NotaryOfficeRequest {
  name: string;
  address: string;
  phoneNumber: string;
  workingHours: string;
  isActive: boolean;
}

