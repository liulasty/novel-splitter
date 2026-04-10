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

export function getApiErrorMessage(error: unknown, fallback: string): string {
  const e = error as MaybeAxiosLikeError | undefined;
  const dataMessage = e?.response?.data?.message;
  if (typeof dataMessage === 'string' && dataMessage.trim().length > 0) {
    return dataMessage;
  }
  const dataError = e?.response?.data?.error;
  if (typeof dataError === 'string' && dataError.trim().length > 0) {
    return dataError;
  }
  if (typeof e?.message === 'string' && e.message.trim().length > 0) {
    return e.message;
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
