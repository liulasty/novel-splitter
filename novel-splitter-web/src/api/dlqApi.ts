import { apiClient } from './client';

export interface DlqStat {
  queueName: string;
  targetRoutingKey: string;
  messageCount: number;
}

export interface DlqRequeueResult {
  queueName: string;
  requeued: number;
  remaining: number;
}

export const dlqApi = {
  getStats: () => apiClient.get<DlqStat[]>('/system/dlq/stats'),

  requeue: (queueName: string, maxMessages = 10_000) =>
    apiClient.post<DlqRequeueResult>(
      `/system/dlq/${encodeURIComponent(queueName)}/requeue`,
      null,
      { params: { maxMessages } }
    ),
};
