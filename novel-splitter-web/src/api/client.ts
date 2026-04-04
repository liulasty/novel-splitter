import axios from 'axios';

export const apiClient = axios.create({
  baseURL: '/api',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT) || 300000,
});

const enableApiLog = import.meta.env.VITE_ENABLE_API_LOG === 'true';

if (enableApiLog) {
  apiClient.interceptors.request.use(
    (config) => {
      console.log(`[API Request] -> ${config.method?.toUpperCase()} ${config.url}`, config);
      return config;
    },
    (error) => {
      console.error('[API Request Error]', error);
      return Promise.reject(error);
    }
  );
}

apiClient.interceptors.response.use(
  (response) => {
    if (enableApiLog) {
      console.log(`[API Response] <- ${response.config.method?.toUpperCase()} ${response.config.url}`, response);
    }
    return response;
  },
  (error) => {
    if (enableApiLog) {
      console.error(`[API Response Error] <- ${error.config?.method?.toUpperCase()} ${error.config?.url}`, error);
    } else {
      console.error('API Error:', error);
    }
    return Promise.reject(error);
  }
);
