import { useState, useEffect, useRef } from 'react';
import { taskApi } from '@/api/taskApi';
import type { SplitTask } from '@/api/taskApi';

export interface DetailProgressEvent {
  taskId: string;
  progress: number;
  message: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'CANCELLED';
  timestamp: number;
}

const INITIAL_POLL_INTERVAL = 1000; // 1s
const MAX_POLL_INTERVAL = 10000;    // 10s
const POLL_MULTIPLIER = 1.5;        // 降频倍数
const ERROR_BACKOFF_MULTIPLIER = 2; // 错误退避倍数

export function useTaskDetail(taskId: string | null) {
  const [task, setTask] = useState<SplitTask | null>(null);
  const [logs, setLogs] = useState<DetailProgressEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  // 记录轮询相关状态，避免闭包陷阱
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const currentIntervalRef = useRef(INITIAL_POLL_INTERVAL);
  const lastProgressRef = useRef<number>(-1);
  const isMountedRef = useRef(true);

  useEffect(() => {
    isMountedRef.current = true;
    
    if (!taskId) {
      setTask(null);
      setLogs([]);
      setError(null);
      return;
    }

    const fetchInitialData = async () => {
      try {
        setLoading(true);
        const [data, historyEvents] = await Promise.all([
          taskApi.getTask(taskId),
          taskApi.getTaskEvents(taskId)
        ]);

        if (isMountedRef.current) {
          setTask(data);
          lastProgressRef.current = data.progress;
          
          if (historyEvents && historyEvents.length > 0) {
            setLogs(historyEvents);
          } else {
            setLogs([{
              taskId: data.taskId,
              progress: data.progress,
              message: data.message || '任务初始状态',
              status: data.status as DetailProgressEvent['status'],
              timestamp: Date.now()
            }]);
          }
          
          // 如果任务尚未结束，则开始智能轮询
          if (data.status === 'PENDING' || data.status === 'PROCESSING') {
             startSmartPolling();
          }
        }
      } catch (err: any) {
        if (isMountedRef.current) setError(err.message || '获取任务详情失败');
      } finally {
        if (isMountedRef.current) setLoading(false);
      }
    };

    const startSmartPolling = () => {
      const poll = async () => {
        if (!isMountedRef.current) return;
        
        try {
          // 获取上次日志中最后一条的时间戳，如果存在的话
          const lastEventTimestamp = logs.length > 0 ? logs[logs.length - 1].timestamp : undefined;
          
          const [currentTask, newEvents] = await Promise.all([
             taskApi.getTask(taskId),
             taskApi.getTaskEvents(taskId, lastEventTimestamp)
          ]);
          
          if (!isMountedRef.current) return;
          
          setTask(currentTask);
          if (newEvents && newEvents.length > 0) {
             setLogs(prev => [...prev, ...newEvents]);
          }
          
          // 状态完成，停止轮询
          if (currentTask.status === 'SUCCESS' || currentTask.status === 'FAILED' || (currentTask as any).status === 'CANCELLED') {
             return;
          }
          
          // 动态调整轮询频率
          if (currentTask.progress === lastProgressRef.current) {
             // 进度无变化：降频轮询
             currentIntervalRef.current = Math.min(currentIntervalRef.current * POLL_MULTIPLIER, MAX_POLL_INTERVAL);
          } else {
             // 进度有变化：重置为高速轮询
             currentIntervalRef.current = INITIAL_POLL_INTERVAL;
             lastProgressRef.current = currentTask.progress;
          }
          
          // 调度下一次轮询
          pollTimerRef.current = setTimeout(poll, currentIntervalRef.current);
          
        } catch (err) {
           console.error('智能轮询请求失败:', err);
           if (!isMountedRef.current) return;
           
           // 错误退避
           currentIntervalRef.current = Math.min(currentIntervalRef.current * ERROR_BACKOFF_MULTIPLIER, MAX_POLL_INTERVAL);
           pollTimerRef.current = setTimeout(poll, currentIntervalRef.current);
        }
      };
      
      // 启动首次轮询
      pollTimerRef.current = setTimeout(poll, currentIntervalRef.current);
    };

    // 清理上一次的定时器并重置状态
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    currentIntervalRef.current = INITIAL_POLL_INTERVAL;
    lastProgressRef.current = -1;

    fetchInitialData();

    return () => {
      isMountedRef.current = false;
      if (pollTimerRef.current) {
        clearTimeout(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, [taskId]);

  return { task, logs, loading, error };
}
