import { http } from './http';
import type { SignInView } from '@/types/api';

export function signInfoApi() {
  return http.get<unknown, SignInView>('/users/sign');
}

export function signInApi() {
  return http.post<unknown, SignInView>('/users/sign', {});
}
