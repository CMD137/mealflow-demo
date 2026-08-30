import axios from 'axios';
import type { Result } from '@/types/api';

const TOKEN_KEY = 'mealflow.user.token';
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 12000
});

export interface ApiError {
  status?: number;
  code?: string;
  message: string;
}

export function errorMessage(error: unknown, fallback = '请求失败') {
  return typeof error === 'object' && error !== null && 'message' in error
    ? String(error.message || fallback) : fallback;
}

function toApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    return {
      status: error.response?.status,
      code: error.response?.data?.code,
      message: error.response?.data?.message || error.message || '网络请求失败'
    };
  }
  if (typeof error === 'object' && error !== null && 'message' in error) {
    return error as ApiError;
  }
  return { message: '请求失败' };
}

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
        return Promise.reject({ code: payload.code, message: payload.message || payload.code || '请求失败' } satisfies ApiError);
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
    return Promise.reject(toApiError(error));
  }
);

export function saveToken(token: string) {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function readToken() {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
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
