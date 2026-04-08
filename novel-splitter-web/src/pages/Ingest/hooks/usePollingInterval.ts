import { useState, useEffect, useRef, useCallback } from 'react';

export function usePollingInterval(isActive: boolean) {
  const [isHidden, setIsHidden] = useState(document.hidden);
  
  const consecutiveErrors = useRef(0);
  const startTime = useRef<number | null>(null);

  useEffect(() => {
    const handleVisibilityChange = () => setIsHidden(document.hidden);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, []);

  const getNextInterval = useCallback((hasError: boolean): number | false => {
    if (!isActive) {
      consecutiveErrors.current = 0;
      startTime.current = null;
      return false;
    }

    if (!startTime.current) {
      startTime.current = Date.now();
    }

    const elapsed = Date.now() - startTime.current;
    
    // 超过 5 分钟暂停轮询
    if (elapsed > 5 * 60 * 1000) {
      return false;
    }

    if (hasError) {
      consecutiveErrors.current += 1;
      // 指数退避: 2s, 4s, 8s, 16s, max 30s
      return Math.min(30000, 2000 * Math.pow(2, consecutiveErrors.current - 1));
    }

    consecutiveErrors.current = 0;
    return isHidden ? 10000 : 2000;
  }, [isActive, isHidden]);

  return getNextInterval;
}
