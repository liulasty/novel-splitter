import { apiClient, type ApiEnvelope } from './client';
import type { DownloadAndIngestRequest } from '@/types/api';
import type { TaskSubmitResponse } from './novelApi';

export const downloadApi = {
  downloadAndIngest: async (request: DownloadAndIngestRequest): Promise<TaskSubmitResponse> => {
    const response = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
      '/v1/download/ingest',
      request
    );
    return response;
  },
};
