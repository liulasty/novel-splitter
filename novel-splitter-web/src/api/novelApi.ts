import { apiClient } from './client';
import type { IngestRequest, NovelUploadResponse } from '@/types/api';

export const novelApi = {
  getNovels: async (): Promise<string[]> => {
    const response = await apiClient.get<string[]>('/novels');
    return response.data;
  },

  uploadNovel: async (file: File): Promise<NovelUploadResponse> => {
    const formData = new FormData();
    formData.append('file', file);
    
    const response = await apiClient.post<NovelUploadResponse>('/novels/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return response.data;
  },

  ingestNovel: async (request: IngestRequest): Promise<{ message: string }> => {
    const response = await apiClient.post<{ message: string }>('/novels/ingest', request);
    return response.data;
  },
};
