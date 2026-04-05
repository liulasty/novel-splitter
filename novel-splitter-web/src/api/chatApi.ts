import { apiClient } from './client';
import type { ChatRequest, Answer } from '@/types/api';

export const chatApi = {
  sendMessage: async (request: ChatRequest): Promise<Answer> => {
    const response = await apiClient.post<Answer>('/chat', request);
    return response.data;
  },
};
