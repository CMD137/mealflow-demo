import axios from 'axios';
import { ElMessage } from 'element-plus';
import type { Result } from '@/types/api';

const TOKEN_KEY = 'mealflow.token';

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10000
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const body = response.data as Result<unknown>;
    if (body && typeof body.success === 'boolean') {
      if (!body.success) {
        ElMessage.error(body.message || body.code || '请求失败');
        return Promise.reject(new Error(body.message || body.code));
      }
      return body.data;
    }
    return response.data;
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络请求失败');
    return Promise.reject(error);
  }
);

export function saveToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function readToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}
