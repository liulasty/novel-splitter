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
  taskType?: 'SPLIT' | 'EMBED';
  sceneCount?: number;
  embeddedCount?: number;
  logs?: string[];
}

export interface TaskProgressEvent {
  taskId: string;
  progress: number;
  message: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
  timestamp: number;
}

export interface JobStatSummaryDto {
  running: number;
  waiting: number;
  completedToday: number;
  failedToday: number;
}

export interface JobRecordDto {
  id: string;
  type: string;
  status: string;
  createTime: string;
  // ... other fields based on backend
}

export const taskApi = {
  getAllTasks: async (): Promise<SplitTask[]> => {
    const response = await apiClient.get<SplitTask[]>('/tasks');
    return response;
  },

  getJobStats: async (): Promise<JobStatSummaryDto> => {
    const response = await apiClient.get<JobStatSummaryDto>('/jobs/stats');
    return response;
  },

  getJobs: async (): Promise<JobRecordDto[]> => {
    const response = await apiClient.get<JobRecordDto[]>('/jobs');
    return response;
  },

  getTask: async (taskId: string): Promise<SplitTask> => {
    const response = await apiClient.get<SplitTask>(`/tasks/${taskId}`);
    return response;
  },

  getTaskEvents: async (taskId: string, sinceTimestamp?: number): Promise<TaskProgressEvent[]> => {
    const params = sinceTimestamp ? { sinceTimestamp } : undefined;
    const response = await apiClient.get<TaskProgressEvent[]>(`/tasks/${taskId}/events`, { params });
    return response;
  },

  deleteTask: async (taskId: string): Promise<void> => {
    await apiClient.delete(`/tasks/${taskId}`);
  }
};
