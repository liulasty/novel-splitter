import axios, { AxiosRequestConfig } from 'axios';
import { toast } from 'sonner';

// 扩展 AxiosRequestConfig 支持自定义属性，并修改拦截器返回值类型
declare module 'axios' {
  export interface AxiosRequestConfig {
    skipInterceptor?: boolean;
    returnFullResponse?: boolean;
  }
  
  // 重写 axios 的响应类型，让业务层调用 get/post 等方法时，自动推导返回 T 类型而不是 AxiosResponse<T>
  export interface AxiosInstance {
    request<T = any, R = T, D = any>(config: AxiosRequestConfig<D>): Promise<R>;
    get<T = any, R = T, D = any>(url: string, config?: AxiosRequestConfig<D>): Promise<R>;
    delete<T = any, R = T, D = any>(url: string, config?: AxiosRequestConfig<D>): Promise<R>;
    head<T = any, R = T, D = any>(url: string, config?: AxiosRequestConfig<D>): Promise<R>;
    options<T = any, R = T, D = any>(url: string, config?: AxiosRequestConfig<D>): Promise<R>;
    post<T = any, R = T, D = any>(url: string, data?: D, config?: AxiosRequestConfig<D>): Promise<R>;
    put<T = any, R = T, D = any>(url: string, data?: D, config?: AxiosRequestConfig<D>): Promise<R>;
    patch<T = any, R = T, D = any>(url: string, data?: D, config?: AxiosRequestConfig<D>): Promise<R>;
  }
}

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

    // 前端逃生舱：如果请求配置了 skipInterceptor 或 returnFullResponse，则跳过解包原样返回
    if (response.config.skipInterceptor || response.config.returnFullResponse) {
      return response;
    }

    const resData = response.data;
    
    // 如果返回的不是 JSON 对象（如 Blob、ArrayBuffer 等下载文件场景），直接返回
    if (!(resData instanceof Object) || resData instanceof Blob || resData instanceof ArrayBuffer) {
        return resData;
    }
    
    // 业务层面解包 (HTTP 200)
    // 后端返回的永远是 { code, message, data }
    if (resData.code !== undefined) {
      if (resData.code === 200) {
        // 业务成功，无感解包
        return resData.data;
      } else {
        // 业务逻辑错误 (code !== 200)
        const errorMessage = resData.message || '业务处理失败';
        toast.error(errorMessage);
        return Promise.reject(new Error(errorMessage));
      }
    }
    
    // 对于没有 code 的普通对象，原样返回
    return resData;
  },
  (error) => {
    if (enableApiLog) {
      console.error(`[API Response Error] <- ${error.config?.method?.toUpperCase()} ${error.config?.url}`, error);
    } else {
      console.error('API Error:', error);
    }

    // 前端逃生舱：跳过错误拦截
    if (error.config?.skipInterceptor) {
        return Promise.reject(error);
    }

    // 统一处理后端返回的 HTTP 状态码错误 (如 401, 403, 404, 500)
    const data = error.response?.data;
    let errorMessage = '网络请求失败，请稍后重试';

    if (data && typeof data === 'object' && data.message) {
      errorMessage = data.message;
    } else if (error.message) {
      errorMessage = error.message;
    }

    // 弹窗提示
    toast.error(errorMessage);

    // 针对 401 状态码特殊处理（例如跳转登录或清空状态）
    if (error.response?.status === 401) {
      console.warn('Unauthorized access - 401');
      // 可以触发全局事件或路由跳转，例如：
      // window.dispatchEvent(new CustomEvent('auth-expired'));
    }

    return Promise.reject(error);
  }
);
