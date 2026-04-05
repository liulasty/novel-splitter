import { useState, useEffect } from 'react';
import { taskApi } from '@/api/taskApi';
import type { SplitTask } from '@/api/taskApi';

export interface DetailProgressEvent {
  taskId: string;
  progress: number;
  message: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';
  timestamp: number;
}

export function useTaskDetail(taskId: string | null) {
  const [task, setTask] = useState<SplitTask | null>(null);
  const [logs, setLogs] = useState<DetailProgressEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!taskId) {
      setTask(null);
      setLogs([]);
      setError(null);
      return;
    }

    let isMounted = true;
    let eventSource: EventSource | null = null;

    const fetchInitialData = async () => {
      try {
        setLoading(true);
        const [data, historyEvents] = await Promise.all([
          taskApi.getTask(taskId),
          taskApi.getTaskEvents(taskId)
        ]);

        if (isMounted) {
          setTask(data);
          
          if (historyEvents && historyEvents.length > 0) {
            setLogs(historyEvents);
          } else {
            // Fallback to current state if no history exists yet
            setLogs([{
              taskId: data.taskId,
              progress: data.progress,
              message: data.message || '任务初始状态',
              status: data.status,
              timestamp: Date.now()
            }]);
          }
          
          // Only open SSE if not finished
          if (data.status === 'PENDING' || data.status === 'PROCESSING') {
            connectSSE();
          }
        }
      } catch (err: any) {
        if (isMounted) setError(err.message || '获取任务详情失败');
      } finally {
        if (isMounted) setLoading(false);
      }
    };

    const connectSSE = () => {
      // Connect to the specific task stream
      eventSource = new EventSource(`/api/tasks/${taskId}/stream`);

      eventSource.addEventListener('progress', (e: MessageEvent) => {
        try {
          const data = JSON.parse(e.data) as DetailProgressEvent;
          if (isMounted) {
            setTask(prev => prev ? { ...prev, progress: data.progress, status: data.status, message: data.message } : prev);
            setLogs(prev => {
              // Avoid exact duplicate consecutive messages
              if (prev.length > 0 && prev[prev.length - 1].message === data.message && prev[prev.length - 1].progress === data.progress) {
                return prev;
              }
              return [...prev, data];
            });

            if (data.status === 'SUCCESS' || data.status === 'FAILED') {
              eventSource?.close();
            }
          }
        } catch (err) {
          console.error('Failed to parse detail SSE data', err);
        }
      });

      eventSource.onerror = () => {
        if (isMounted) {
          setError('日志流连接断开');
        }
        eventSource?.close();
      };
    };

    fetchInitialData();

    return () => {
      isMounted = false;
      if (eventSource) {
        eventSource.close();
      }
    };
  }, [taskId]);

  return { task, logs, loading, error };
}
