import { http } from './http';
import type { AddressRequest, AddressView, LoginRequest, LoginResponse, UserView } from '@/types/api';

export function loginApi(payload: LoginRequest) {
  return http.post<unknown, LoginResponse>('/auth/login', payload);
}

export function requestLoginCodeApi(phone: string) {
  return http.post('/auth/codes', { phone });
}

export function meApi() {
  return http.get<unknown, UserView>('/users/me');
}

export function addressesApi() {
  return http.get<unknown, AddressView[]>('/users/addresses');
}

export function addAddressApi(payload: AddressRequest) {
  return http.post<unknown, AddressView>('/users/addresses', payload);
}

export function updateAddressApi(addressId: number, payload: AddressRequest) {
  return http.put<unknown, AddressView>(`/users/addresses/${addressId}`, payload);
}

export function deleteAddressApi(addressId: number) {
  return http.delete<unknown, void>(`/users/addresses/${addressId}`);
}

export function setDefaultAddressApi(addressId: number) {
  return http.put<unknown, AddressView>(`/users/addresses/${addressId}/default`);
}
