import { api } from '../../lib/http';
import type { SignUpRequest, UserResponse } from '../../types/auth';
import type {
  ContractTemplate,
  DocumentRequirementConfig,
  DocumentRequirementConfigRequest,
  DocumentType,
  DocumentTypeRequest,
  NotaryOffice,
  NotaryOfficeRequest,
  NotaryServiceType,
  NotaryServiceTypeRequest,
} from '../../types/admin';

export interface DashboardSummary {
  totalUsers: number;
  totalNotaries: number;
  pendingRequests: number;
  completedRequests: number;
}

export interface RevenueData {
  month: string;
  revenue: number;
}

export interface RequestsChartData {
  serviceType: string;
  count: number;
}

export interface BlockchainSummary {
  networkName: string;
  chainId: number;
  latestBlock: number;
  totalTransactions: number;
  confirmedTransactions: number;
  totalNodes: number;
  activeNodes: number;
  mode: string;
}

export interface BlockchainNode {
  nodeName: string;
  role: string;
  endpoint: string | null;
  validatorAddress: string | null;
  status: string;
  peerCount: number | null;
  blockHeight: number;
}

export interface BlockchainTransaction {
  transactionId: string;
  requestId: string;
  documentId: string;
  requestCode: string;
  documentHash: string;
  transactionHash: string;
  blockNumber: number;
  networkName: string;
  chainId: number;
  status: string;
  nodeName: string;
  createdAt: string;
  confirmedAt: string;
}

interface ApiEnvelope<T> {
  status: number;
  message: string;
  data: T;
}

interface PagedData<T> {
  content: T[];
}

export async function createNotaryApi(payload: SignUpRequest): Promise<UserResponse> {
  const response = await api.post<ApiEnvelope<UserResponse>>('/api/admin/notaries', payload);
  return response.data.data;
}

export async function listNotariesApi(): Promise<UserResponse[]> {
  const response = await api.get<ApiEnvelope<PagedData<UserResponse>>>('/api/admin/users', {
    params: {
      role: 'NOTARY',
      size: 20,
      page: 0,
    },
  });

  return response.data.data.content;
}

export async function listUsersApi(): Promise<UserResponse[]> {
  const response = await api.get<ApiEnvelope<PagedData<UserResponse>>>('/api/admin/users', {
    params: {
      size: 50,
      page: 0,
    },
  });

  return response.data.data.content;
}

export async function toggleUserStatusApi(userId: string): Promise<UserResponse> {
  const response = await api.put<ApiEnvelope<UserResponse>>(`/api/admin/users/${userId}/status`);
  return response.data.data;
}

export async function listServicesApi(): Promise<NotaryServiceType[]> {
  const response = await api.get<ApiEnvelope<PagedData<NotaryServiceType>>>('/api/admin/services', {
    params: { size: 100, page: 0 },
  });
  return response.data.data.content;
}

export async function createServiceApi(payload: NotaryServiceTypeRequest): Promise<NotaryServiceType> {
  const response = await api.post<ApiEnvelope<NotaryServiceType>>('/api/admin/services', payload);
  return response.data.data;
}

export async function updateServiceApi(id: string, payload: NotaryServiceTypeRequest): Promise<NotaryServiceType> {
  const response = await api.put<ApiEnvelope<NotaryServiceType>>(`/api/admin/services/${id}`, payload);
  return response.data.data;
}

export async function deleteServiceApi(id: string): Promise<void> {
  await api.delete(`/api/admin/services/${id}`);
}

export async function listDocumentRequirementConfigsApi(): Promise<DocumentRequirementConfig[]> {
  const response = await api.get<ApiEnvelope<DocumentRequirementConfig[]>>('/api/admin/document-requirements');
  return response.data.data;
}

export async function createDocumentRequirementConfigApi(
  payload: DocumentRequirementConfigRequest,
): Promise<DocumentRequirementConfig> {
  const response = await api.post<ApiEnvelope<DocumentRequirementConfig>>('/api/admin/document-requirements', payload);
  return response.data.data;
}

export async function updateDocumentRequirementConfigApi(
  serviceId: string,
  payload: DocumentRequirementConfigRequest,
): Promise<DocumentRequirementConfig> {
  const response = await api.put<ApiEnvelope<DocumentRequirementConfig>>(
    `/api/admin/document-requirements/${serviceId}`,
    payload,
  );
  return response.data.data;
}

export async function deleteDocumentRequirementConfigApi(serviceId: string): Promise<void> {
  await api.delete(`/api/admin/document-requirements/${serviceId}`);
}

export async function listDocumentTypesApi(): Promise<DocumentType[]> {
  const response = await api.get<ApiEnvelope<DocumentType[]>>('/api/admin/document-types');
  return response.data.data;
}

export async function createDocumentTypeApi(payload: DocumentTypeRequest): Promise<DocumentType> {
  const response = await api.post<ApiEnvelope<DocumentType>>('/api/admin/document-types', payload);
  return response.data.data;
}

export async function updateDocumentTypeApi(code: string, payload: DocumentTypeRequest): Promise<DocumentType> {
  const response = await api.put<ApiEnvelope<DocumentType>>(`/api/admin/document-types/${code}`, payload);
  return response.data.data;
}

export async function listTemplatesApi(serviceTypeId?: string): Promise<ContractTemplate[]> {
  const params: Record<string, string> = { onlyActive: 'false' };
  if (serviceTypeId) params.serviceTypeId = serviceTypeId;
  
  const response = await api.get<ApiEnvelope<ContractTemplate[]>>('/api/templates', { params });
  return response.data.data;
}

export async function createTemplateApi(formData: FormData): Promise<ContractTemplate> {
  const response = await api.post<ApiEnvelope<ContractTemplate>>('/api/admin/templates', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}

export async function updateTemplateApi(id: string, formData: FormData): Promise<ContractTemplate> {
  const response = await api.put<ApiEnvelope<ContractTemplate>>(`/api/admin/templates/${id}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}

export async function deleteTemplateApi(id: string): Promise<void> {
  await api.delete(`/api/admin/templates/${id}`);
}

export async function downloadTemplateApi(id: string): Promise<Blob> {
  const response = await api.get<Blob>(`/api/templates/${id}/view`, { responseType: 'blob' });
  return response.data;
}

export async function listOfficesApi(): Promise<NotaryOffice[]> {
  const response = await api.get<ApiEnvelope<PagedData<NotaryOffice>>>('/api/admin/offices', {
    params: { size: 100, page: 0 },
  });
  return response.data.data.content;
}

export async function listActiveOfficesApi(): Promise<NotaryOffice[]> {
  const response = await api.get<ApiEnvelope<PagedData<NotaryOffice>>>('/api/notary-offices', {
    params: { size: 100, page: 0 },
  });
  return response.data.data.content;
}

export async function createOfficeApi(payload: NotaryOfficeRequest): Promise<NotaryOffice> {
  const response = await api.post<ApiEnvelope<NotaryOffice>>('/api/admin/offices', payload);
  return response.data.data;
}

export async function updateOfficeApi(id: string, payload: NotaryOfficeRequest): Promise<NotaryOffice> {
  const response = await api.put<ApiEnvelope<NotaryOffice>>(`/api/admin/offices/${id}`, payload);
  return response.data.data;
}

export async function deleteOfficeApi(id: string): Promise<void> {
  await api.delete(`/api/admin/offices/${id}`);
}

export async function getDashboardSummaryApi(): Promise<DashboardSummary> {
  const response = await api.get<DashboardSummary>('/api/admin/dashboard/summary');
  return response.data;
}

export async function getDashboardRevenueApi(): Promise<RevenueData[]> {
  const response = await api.get<RevenueData[]>('/api/admin/dashboard/revenue');
  return response.data;
}

export async function getDashboardRequestsChartApi(): Promise<RequestsChartData[]> {
  const response = await api.get<RequestsChartData[]>('/api/admin/dashboard/requests-chart');
  return response.data;
}

export async function getBlockchainSummaryApi(): Promise<BlockchainSummary> {
  const response = await api.get<ApiEnvelope<BlockchainSummary>>('/api/admin/blockchain/summary');
  return response.data.data;
}

export async function listBlockchainNodesApi(): Promise<BlockchainNode[]> {
  const response = await api.get<ApiEnvelope<BlockchainNode[]>>('/api/admin/blockchain/nodes');
  return response.data.data;
}

export async function listBlockchainTransactionsApi(): Promise<BlockchainTransaction[]> {
  const response = await api.get<ApiEnvelope<BlockchainTransaction[]>>('/api/admin/blockchain/transactions');
  return response.data.data;
}

