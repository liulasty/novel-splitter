import path from "path"
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig(({ command }) => {
  const envMode = command === 'serve' ? 'dev' : 'prod';
  const env = loadEnv(envMode, path.resolve(__dirname, '../config'), '');
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          timeout: 300000,
          proxyTimeout: 300000,
        },
      },
    },
  };
});
