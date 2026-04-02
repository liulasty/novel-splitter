import axios from 'axios';

const apiClient = axios.create({
  baseURL: '/api',
});

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

export const taskApi = {
  getAllTasks: async (): Promise<SplitTask[]> => {
    const response = await apiClient.get<SplitTask[]>('/tasks');
    return response.data;
  },

  getTask: async (taskId: string): Promise<SplitTask> => {
    const response = await apiClient.get<SplitTask>(`/tasks/${taskId}`);
    return response.data;
  },

  deleteTask: async (taskId: string): Promise<void> => {
    await apiClient.delete(`/tasks/${taskId}`);
  }
};
