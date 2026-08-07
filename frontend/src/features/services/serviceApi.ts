import { api } from '../../lib/http';
import type { NotaryServiceType } from '../../types/admin';

interface ApiEnvelope<T> {
  status: number;
  message: string;
  data: T;
}

interface PagedData<T> {
  content: T[];
}

export async function listActiveServicesApi(): Promise<NotaryServiceType[]> {
  const response = await api.get<ApiEnvelope<PagedData<NotaryServiceType>>>('/api/services', {
    params: { size: 100, page: 0 },
  });
  return response.data.data.content;
}
