import { api } from '../../lib/http';
import type { PaymentItem } from '../../types/payment';

interface ApiEnvelope<T> {
  status: number;
  message: string;
  data: T;
}

export async function getPaymentByRequestApi(requestId: string): Promise<PaymentItem> {
  const response = await api.get<ApiEnvelope<PaymentItem>>(`/api/payments/request/${requestId}`);
  return response.data.data;
}

export async function confirmPaymentTransferApi(paymentId: string): Promise<PaymentItem> {
  const response = await api.post<ApiEnvelope<PaymentItem>>(`/api/payments/${paymentId}/confirm-transfer`);
  return response.data.data;
}
