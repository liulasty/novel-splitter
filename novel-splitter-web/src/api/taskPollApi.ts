import { apiClient, type ApiEnvelope } from './client';
import type { SplitTask } from './taskApi';

export const taskPollApi = {
  pollTasks: async (taskIds: string[]): Promise<SplitTask[]> => {
    if (!taskIds || taskIds.length === 0) return [];
    const uniqueIds = Array.from(new Set(taskIds));
    if (uniqueIds.length > 20) {
      throw new Error('At most 20 task IDs are allowed per polling request');
    }
    const params = new URLSearchParams();
    uniqueIds.forEach(id => params.append('ids', id));
    // Since baseURL is /api, we call /tasks/poll
    const response = await apiClient.get<ApiEnvelope<SplitTask[]>, SplitTask[]>('/tasks/poll', { params });
    return response;
  },

  pollByNovelId: async (novelId: string): Promise<SplitTask[]> => {
    if (!novelId) return [];
    const response = await apiClient.get<ApiEnvelope<SplitTask[]>, SplitTask[]>('/tasks/poll', {
      params: { novelId },
    });
    return response;
  }
};
