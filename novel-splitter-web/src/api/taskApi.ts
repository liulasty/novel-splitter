import { apiClient } from './client';

export interface SplitTask {
  taskId: string;
  novelId: string;
  fileName: string;
  maxScenes: number;
  version: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
  progress: number;
  message: string;
  createdAt: number;
  updatedAt: number;
}

export interface TaskProgressEvent {
  taskId: string;
  progress: number;
  message: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
  timestamp: number;
}

export const taskApi = {
  getAllTasks: async (): Promise<SplitTask[]> => {
    const response = await apiClient.get<SplitTask[]>('/tasks');
    return response.data;
  },

  getTask: async (taskId: string): Promise<SplitTask> => {
    const response = await apiClient.get<SplitTask>(`/tasks/${taskId}`);
    return response.data;
  },

  getTaskEvents: async (taskId: string): Promise<TaskProgressEvent[]> => {
    const response = await apiClient.get<TaskProgressEvent[]>(`/tasks/${taskId}/events`);
    return response.data;
  },

  deleteTask: async (taskId: string): Promise<void> => {
    await apiClient.delete(`/tasks/${taskId}`);
  }
};
