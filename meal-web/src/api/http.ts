import axios from 'axios';
import type { Result } from '@/types/api';

const TOKEN_KEY = 'mealflow.token';
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000
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
  const token = sessionStorage.getItem(TOKEN_KEY);
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
        return Promise.reject({ code: body.code, message: body.message || body.code || '请求失败' } satisfies ApiError);
      }
      return body.data;
    }
    return response.data;
  },
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.removeItem(TOKEN_KEY);
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
