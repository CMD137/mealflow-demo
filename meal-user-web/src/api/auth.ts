import { http } from './http';
import type { LoginRequest, LoginResponse, UserView } from '@/types/api';

export function loginApi(payload: LoginRequest) {
  return http.post<unknown, LoginResponse>('/auth/login', payload);
}

export function requestLoginCodeApi(phone: string) {
  return http.post('/auth/codes', { phone });
}

export function meApi() {
  return http.get<unknown, UserView>('/users/me');
}
