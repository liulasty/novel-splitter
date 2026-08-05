import { toast } from 'sonner';

type MaybeAxiosLikeError = {
  response?: {
    status?: number;
    data?: {
      message?: string;
      error?: string;
    };
  };
  message?: string;
};

export function isHttpConflict409(error: unknown): boolean {
  const e = error as MaybeAxiosLikeError | undefined;
  return e?.response?.status === 409;
}

/**
 * 已知后端英文报错 → 友好中文；已是中文或未命中模式则原样返回（保留调试信息）。
 * 顺序敏感：更具体的规则在前，兜底 `not found` 在最后。
 */
const ZH_ERROR_RULES: Array<{ pattern: RegExp; zh: string }> = [
  { pattern: /Novel has running tasks; cannot delete knowledge base right now\.?/i, zh: '该小说存在运行中任务，暂不可删除知识库，请等待完成后再试' },
  { pattern: /Novel has running tasks; cannot delete right now\.?/i, zh: '该小说存在运行中任务，暂不可删除，请等待完成后再试' },
  { pattern: /Novel not found/i, zh: '该小说不存在或已被删除' },
  { pattern: /Task is running; cannot delete/i, zh: '任务运行中，暂不可删除，请等待完成后再试' },
  { pattern: /Task not found/i, zh: '任务不存在或已被清理' },
  { pattern: /Version not found/i, zh: '版本不存在' },
  { pattern: /collection .* not found/i, zh: '向量集合不存在' },
  { pattern: /not found/i, zh: '数据不存在或已被删除' },
];

export function zhFriendlyError(raw: string): string {
  if (!raw) return raw;
  const msg = raw.trim();
  if (!msg) return msg;
  // 已是中文则原样返回
  if (/[一-龥]/.test(msg)) return msg;
  for (const { pattern, zh } of ZH_ERROR_RULES) {
    if (pattern.test(msg)) return zh;
  }
  return msg;
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  const e = error as MaybeAxiosLikeError | undefined;
  const dataMessage = e?.response?.data?.message;
  if (typeof dataMessage === 'string' && dataMessage.trim().length > 0) {
    return zhFriendlyError(dataMessage);
  }
  const dataError = e?.response?.data?.error;
  if (typeof dataError === 'string' && dataError.trim().length > 0) {
    return zhFriendlyError(dataError);
  }
  if (typeof e?.message === 'string' && e.message.trim().length > 0) {
    return zhFriendlyError(e.message);
  }
  return fallback;
}

export function handleConflict409(error: unknown, message: string): boolean {
  if (!isHttpConflict409(error)) {
    return false;
  }
  toast.error(message);
  return true;
}
