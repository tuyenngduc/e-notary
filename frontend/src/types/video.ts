import type { ContractTemplate } from './admin';
import type { Appointment, DocumentItem } from './request';

export type VideoSessionStatus =
  | 'PENDING'
  | 'NOTARY_JOINED'
  | 'IN_PROGRESS'
  | 'FINISHED'
  | 'CANCELLED'
  | string;

export interface VideoSessionResponse {
  sessionId: string;
  appointmentId: string | null;
  appointment?: Appointment | null;
  requestId?: string | null;
  selectedTemplate?: ContractTemplate | null;
  draftDocument?: DocumentItem | null;
  requiresTemplate?: boolean;
  sessionToken: string;
  meetingUrl: string;
  roomId: string;
  status: VideoSessionStatus;
  notaryJoinedAt: string | null;
  clientJoinedAt: string | null;
  endedAt: string | null;
  durationSeconds: number | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SignVideoDocumentResponse {
  signedDocument: DocumentItem;
  clientSigned: boolean;
  notarySigned: boolean;
  completed: boolean;
  requestStatus: string;
}
