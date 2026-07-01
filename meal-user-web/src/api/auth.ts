import { http } from './http';
import type { LoginRequest, LoginResponse, UserView } from '@/types/api';

export function loginApi(payload: LoginRequest) {
  return http.post<unknown, LoginResponse>('/auth/login', payload);
}

export function meApi() {
  return http.get<unknown, UserView>('/users/me');
}
