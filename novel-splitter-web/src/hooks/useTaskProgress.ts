import { useState, useEffect } from 'react';

export interface ProgressEvent {
  taskId: string;
  progress: number;      // 0-100，-1 表示失败
  message: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED';
  timestamp: number;
}

export interface TaskProgressState {
  progress: number;
  message: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'PENDING';
  stageHistory: string[];
}

export function useTaskProgress(taskId: string | null) {
  const [state, setState] = useState<TaskProgressState>({
    progress: 0,
    message: '',
    status: 'PENDING',
    stageHistory: [],
  });

  useEffect(() => {
    if (!taskId) {
      setState({
        progress: 0,
        message: '',
        status: 'PENDING',
        stageHistory: [],
      });
      return;
    }

    setState({ progress: 0, message: '连接进度流...', status: 'RUNNING', stageHistory: [] });

    const eventSource = new EventSource(`/api/novels/progress/stream?taskId=${taskId}`);

    const handleProgress = (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data) as ProgressEvent;
        
        setState((prev) => {
          // 乱序保护
          if (data.status !== 'COMPLETED' && data.status !== 'FAILED' && data.progress < prev.progress) {
            return prev;
          }

          const newHistory = [...prev.stageHistory];
          if (data.message && data.message !== prev.message) {
             newHistory.push(data.message);
          }

          return {
            progress: data.progress,
            message: data.message,
            status: data.status,
            stageHistory: newHistory,
          };
        });

        if (data.status === 'COMPLETED' || data.status === 'FAILED') {
          eventSource.close();
        }
      } catch (err) {
        console.error('Failed to parse SSE data', err);
      }
    };

    eventSource.addEventListener('progress', handleProgress);

    eventSource.onerror = () => {
      setState(prev => {
        if (prev.status === 'COMPLETED') return prev;
        return {
          ...prev,
          status: 'FAILED',
          message: '连接失败，请重试',
        };
      });
      eventSource.close();
    };

    return () => {
      eventSource.removeEventListener('progress', handleProgress);
      eventSource.close();
    };
  }, [taskId]);

  return state;
}
