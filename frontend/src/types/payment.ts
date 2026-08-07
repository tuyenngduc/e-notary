export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | string;

export interface PaymentItem {
  paymentId: string;
  requestId: string;
  amount: number;
  paymentStatus: PaymentStatus;
  paymentMethod?: string | null;
  transactionReference?: string | null;
  transferContent: string;
  bankCode: string;
  accountNumber: string;
  accountName: string;
  qrImageUrl: string;
  createdAt: string;
  paidAt?: string | null;
}
