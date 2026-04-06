import axios from 'axios';
import { toast } from 'sonner';

export const apiClient = axios.create({
  baseURL: '/api',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT) || 300000,
});

const enableApiLog = import.meta.env.VITE_ENABLE_API_LOG === 'true';

// 注册请求拦截器（始终注册，用于注入 Auth Token）
apiClient.interceptors.request.use(
  (config) => {
    // 从 localStorage 读取 Token 并注入 Authorization Header
    const token = localStorage.getItem('API_AUTH_TOKEN');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    if (enableApiLog) {
      console.log(`[API Request] -> ${config.method?.toUpperCase()} ${config.url}`, config);
    }
    return config;
  },
  (error) => {
    if (enableApiLog) {
      console.error('[API Request Error]', error);
    }
    return Promise.reject(error);
  }
);

apiClient.interceptors.response.use(
  (response) => {
    if (enableApiLog) {
      console.log(`[API Response] <- ${response.config.method?.toUpperCase()} ${response.config.url}`, response);
    }
    
    // 统一解包后端 ApiResponse 格式
    // 采用替换 response.data 的方式，避免破坏所有上层 API 的 `return response.data` 签名
    if (response.data && response.data.code === 200) {
      response.data = response.data.data;
    }
    
    return response;
  },
  (error) => {
    if (enableApiLog) {
      console.error(`[API Response Error] <- ${error.config?.method?.toUpperCase()} ${error.config?.url}`, error);
    } else {
      console.error('API Error:', error);
    }

    // 统一处理后端返回的 message 并弹窗
    const data = error.response?.data;
    let errorMessage = '网络请求失败，请稍后重试';

    if (data && typeof data === 'object' && data.message) {
      errorMessage = data.message;
    } else if (error.message) {
      errorMessage = error.message;
    }

    // 弹窗提示
    toast.error(errorMessage);

    // 针对 401 状态码特殊处理（例如跳转登录或提示）
    if (error.response?.status === 401) {
      // TODO: 处理 401 逻辑，比如跳转到登录页或清空状态
      console.warn('Unauthorized access - 401');
    }

    return Promise.reject(error);
  }
);
