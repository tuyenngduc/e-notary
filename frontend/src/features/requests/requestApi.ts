import { api } from '../../lib/http';
import type {
  CreateRequestPayload,
  DocType,
  DocumentItem,
  NotaryRequest,
  PagedData,
  RequestStatus,
} from '../../types/request';
import type { VideoSessionResponse } from '../../types/video';
import type { SignVideoDocumentResponse } from '../../types/video';

interface ApiEnvelope<T> {
  status: number;
  message: string;
  data: T;
}

export async function listMyRequestsApi(): Promise<NotaryRequest[]> {
  const response = await api.get<ApiEnvelope<NotaryRequest[]>>('/api/requests/me');
  return response.data.data;
}

export async function getRequestApi(id: string): Promise<NotaryRequest> {
  const response = await api.get<ApiEnvelope<NotaryRequest>>(`/api/requests/${id}`);
  return response.data.data;
}

export async function getRequestDocumentsApi(id: string): Promise<DocumentItem[]> {
  const response = await api.get<ApiEnvelope<DocumentItem[]>>(`/api/requests/${id}/documents`);
  return response.data.data;
}

export async function createRequestApi(payload: CreateRequestPayload): Promise<NotaryRequest> {
  const response = await api.post<ApiEnvelope<NotaryRequest>>('/api/requests', payload);
  return response.data.data;
}

export async function uploadRequestDocumentApi(id: string, file: File, docType: DocType): Promise<DocumentItem> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('docType', docType);

  const response = await api.post<ApiEnvelope<DocumentItem>>(`/api/requests/${id}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}

export async function replaceDocumentApi(documentId: string, file: File): Promise<DocumentItem> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await api.put<ApiEnvelope<DocumentItem>>(`/api/documents/${documentId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}

export async function listNotaryRequestsApi(status?: RequestStatus): Promise<NotaryRequest[]> {
  const response = await api.get<ApiEnvelope<PagedData<NotaryRequest>>>('/api/requests/filter', {
    params: {
      status,
      page: 0,
      size: 50,
    },
  });
  return response.data.data.content;
}

export async function listAcceptedNotaryRequestsApi(): Promise<NotaryRequest[]> {
  const response = await api.get<ApiEnvelope<PagedData<NotaryRequest>>>('/api/requests/me/accepted', {
    params: {
      page: 0,
      size: 50,
    },
  });
  return response.data.data.content;
}

export async function acceptRequestApi(id: string): Promise<NotaryRequest> {
  const response = await api.post<ApiEnvelope<NotaryRequest>>(`/api/requests/${id}/accept`);
  return response.data.data;
}

export async function rejectRequestApi(id: string, reason: string): Promise<NotaryRequest> {
  const response = await api.post<ApiEnvelope<NotaryRequest>>(`/api/requests/${id}/reject`, { reason });
  return response.data.data;
}

export async function cancelRequestApi(id: string): Promise<NotaryRequest> {
  const response = await api.post<ApiEnvelope<NotaryRequest>>(`/api/requests/${id}/cancel`);
  return response.data.data;
}

export async function scheduleRequestApi(id: string, scheduledTimeIso: string, physicalAddress?: string): Promise<void> {
  await api.post(`/api/requests/${id}/schedule`, {
    scheduledTime: scheduledTimeIso,
    physicalAddress: physicalAddress || undefined,
  });
}

export async function downloadDocumentApi(documentId: string): Promise<Blob> {
  const response = await api.get<Blob>(`/api/documents/${documentId}`, { responseType: 'blob' });
  return response.data;
}

export async function viewDocumentApi(documentId: string): Promise<Blob> {
  const response = await api.get<Blob>(`/api/documents/${documentId}/view`, { responseType: 'blob' });
  return response.data;
}

export async function listMyAppointmentsApi(): Promise<import('../../types/request').Appointment[]> {
  const response = await api.get<ApiEnvelope<import('../../types/request').Appointment[]>>('/api/appointments/me');
  return response.data.data;
}

export async function verifyVideoTokenApi(token: string): Promise<VideoSessionResponse> {
  const response = await api.get<ApiEnvelope<VideoSessionResponse>>('/api/video/verify-token', {
    params: { token },
  });
  return response.data.data;
}

export async function joinVideoRoomApi(roomId: string, token: string): Promise<VideoSessionResponse> {
  const response = await api.post<ApiEnvelope<VideoSessionResponse>>(`/api/video/room/${roomId}/join`, null, {
    params: { token },
  });
  return response.data.data;
}

export async function endVideoSessionApi(sessionId: string, reason?: string): Promise<VideoSessionResponse> {
  const response = await api.post<ApiEnvelope<VideoSessionResponse>>(`/api/video/sessions/${sessionId}/end`, null, {
    params: { reason: reason?.trim() || 'Kết thúc từ giao diện' },
  });
  return response.data.data;
}

export async function saveVideoEvidenceApi(sessionId: string, imageData: string): Promise<DocumentItem> {
  const response = await api.post<ApiEnvelope<DocumentItem>>(`/api/video/sessions/${sessionId}/evidence`, {
    imageData,
  });
  return response.data.data;
}

export async function signVideoDocumentApi(
  sessionId: string,
  documentId: string,
  signatureValue: string,
  placement: {
    pageNumber: number;
    xPercent: number;
    yPercent: number;
    widthPercent: number;
    heightPercent: number;
  },
): Promise<SignVideoDocumentResponse> {
  const response = await api.post<ApiEnvelope<SignVideoDocumentResponse>>(`/api/video/sessions/${sessionId}/signatures`, {
    documentId,
    signatureValue,
    ...placement,
  });
  return response.data.data;
}
