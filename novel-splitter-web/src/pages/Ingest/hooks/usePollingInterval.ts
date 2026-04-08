import { useState, useEffect, useRef, useCallback } from 'react';

export function usePollingInterval(isActive: boolean) {
  const [isHidden, setIsHidden] = useState(document.hidden);
  const hiddenSince = useRef<number | null>(document.hidden ? Date.now() : null);
  const consecutiveErrors = useRef(0);

  useEffect(() => {
    const handleVisibilityChange = () => {
      const hidden = document.hidden;
      setIsHidden(hidden);
      hiddenSince.current = hidden ? Date.now() : null;
    };
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, []);

  const getNextInterval = useCallback((hasError: boolean, hasProcessing: boolean): number | false => {
    if (!isActive) {
      consecutiveErrors.current = 0;
      return false;
    }

    // 页面隐藏超过 5 分钟，暂停轮询
    if (isHidden && hiddenSince.current && Date.now() - hiddenSince.current > 5 * 60 * 1000) {
      return false;
    }

    if (hasError) {
      consecutiveErrors.current += 1;
      // 指数退避: 2s, 4s, 8s, 16s，连续 5 次失败后停止
      if (consecutiveErrors.current >= 5) {
        return false;
      }
      return Math.min(30000, 2000 * Math.pow(2, consecutiveErrors.current - 1));
    }

    consecutiveErrors.current = 0;
    if (isHidden) {
      return 10000;
    }
    return hasProcessing ? 2000 : 3000;
  }, [isActive, isHidden]);

  return getNextInterval;
}
