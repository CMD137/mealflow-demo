import axios from 'axios';
import type { Result } from '@/types/api';

const TOKEN_KEY = 'mealflow.user.token';
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 12000
});

http.interceptors.request.use((config) => {
  const token = readToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data as Result<unknown>;
    if (payload && typeof payload === 'object' && 'success' in payload) {
      if (!payload.success) {
        return Promise.reject(new Error(payload.message || payload.code || '请求失败'));
      }
      return payload.data;
    }
    return response.data;
  },
  (error) => {
    if (error?.response?.status === 401) {
      clearToken();
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
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

export function assetUrl(url?: string | null) {
  if (!url) {
    return '';
  }
  if (/^(https?:)?\/\//.test(url) || url.startsWith('data:') || url.startsWith('blob:')) {
    return url;
  }
  if (!url.startsWith('/')) {
    return url;
  }
  if (API_BASE_URL.startsWith('http://') || API_BASE_URL.startsWith('https://')) {
    return `${API_BASE_URL.replace(/\/$/, '')}${url}`;
  }
  return `${API_BASE_URL.replace(/\/$/, '')}${url}`;
}
