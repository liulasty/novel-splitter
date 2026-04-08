import { apiClient } from './client';
import type { SplitTask } from './taskApi';

export const taskPollApi = {
  pollTasks: async (taskIds: string[]): Promise<SplitTask[]> => {
    if (!taskIds || taskIds.length === 0) return [];
    const params = new URLSearchParams();
    taskIds.forEach(id => params.append('taskIds', id));
    // Since baseURL is /api, we call /tasks/poll
    const response = await apiClient.get<SplitTask[]>('/tasks/poll', { params });
    return response;
  }
};
