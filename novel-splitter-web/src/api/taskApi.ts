import { apiClient, type ApiEnvelope } from './client';

export interface SplitTask {
  taskId: string;
  novelId: string;
  novelTitle?: string | null;
  fileName: string;
  maxScenes: number;
  version: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
  progress: number;
  message: string;
  createdAt: number;
  updatedAt: number;
  taskType?: 'LOAD' | 'CHAPTER_PARSE' | 'SCENE_SPLIT' | 'PIPELINE' | 'EMBED';
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

export interface SplitTaskPage {
  content: SplitTask[];
  page: number;
  size: number;
  totalElements: number;
}

export const taskApi = {
  getAllTasks: async (): Promise<SplitTask[]> => {
    const response = await apiClient.get<ApiEnvelope<SplitTask[]>, SplitTask[]>('/tasks');
    return response;
  },

  /** 分页筛选：必须带 page 参数（与 GET /tasks 列表区分） */
  getTasksPage: async (params: {
    page?: number;
    size?: number;
    novelId?: string;
    taskType?: string;
    status?: string;
    updatedFrom?: number;
    updatedTo?: number;
  }): Promise<SplitTaskPage> => {
    const response = await apiClient.get<ApiEnvelope<SplitTaskPage>, SplitTaskPage>('/tasks/list', {
      params: { ...params, page: params.page ?? 0, size: params.size ?? 50 },
    });
    return response;
  },

  getJobStats: async (): Promise<JobStatSummaryDto> => {
    const response = await apiClient.get<ApiEnvelope<JobStatSummaryDto>, JobStatSummaryDto>('/jobs/stats');
    return response;
  },

  getJobs: async (): Promise<JobRecordDto[]> => {
    const response = await apiClient.get<ApiEnvelope<JobRecordDto[]>, JobRecordDto[]>('/jobs');
    return response;
  },

  getTask: async (taskId: string): Promise<SplitTask> => {
    const response = await apiClient.get<ApiEnvelope<SplitTask>, SplitTask>(`/tasks/${taskId}`);
    return response;
  },

  getTaskEvents: async (taskId: string, sinceTimestamp?: number): Promise<TaskProgressEvent[]> => {
    const params = sinceTimestamp ? { sinceTimestamp } : undefined;
    const response = await apiClient.get<ApiEnvelope<TaskProgressEvent[]>, TaskProgressEvent[]>(`/tasks/${taskId}/events`, { params });
    return response;
  },

  deleteTask: async (taskId: string): Promise<void> => {
    await apiClient.delete(`/tasks/${taskId}`);
  }
};
