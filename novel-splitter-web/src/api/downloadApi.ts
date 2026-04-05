import { apiClient } from './client';
import type { DownloadAndIngestRequest } from '@/types/api';

export const downloadApi = {
  downloadAndIngest: async (request: DownloadAndIngestRequest): Promise<{ message: string; taskId?: string }> => {
    const response = await apiClient.post<{ message: string; taskId?: string }>('/v1/download/ingest', request);
    return response.data;
  },
};
