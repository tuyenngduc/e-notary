import { api } from '../../lib/http';

interface ApiEnvelope<T> {
  status: number;
  message: string;
  data: T;
}

export interface PublicDocumentVerificationResult {
  verified: boolean;
  status: 'VERIFIED' | 'NOT_FOUND' | string;
  message: string;
  fileName: string | null;
  fileSize: number | null;
  fileHash: string;
  checkedAt: string;
  requestId: string | null;
  requestCode: string | null;
  requestStatus: string | null;
  contractType: string | null;
  documentId: string | null;
  documentName: string | null;
  documentType: string | null;
  signedSignatureCount: number | null;
  transactionId: string | null;
  transactionHash: string | null;
  blockNumber: number | null;
  networkName: string | null;
  chainId: number | null;
  blockchainStatus: string | null;
  nodeName: string | null;
  confirmedAt: string | null;
}

export async function verifyPublicDocumentApi(file: File): Promise<PublicDocumentVerificationResult> {
  const formData = new FormData();
  formData.append('file', file);

  const response = await api.post<ApiEnvelope<PublicDocumentVerificationResult>>('/api/public/documents/verify', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}
