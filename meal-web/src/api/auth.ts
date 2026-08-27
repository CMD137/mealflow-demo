import { http } from './http';
import type { EmployeeView, LoginRequest, LoginResponse, MenuView, PageResult, RoleView, UserView } from '@/types/api';

export function loginApi(payload: LoginRequest) {
  return http.post<unknown, LoginResponse>('/auth/login', payload);
}

export function requestLoginCodeApi(phone: string) {
  return http.post('/auth/codes', { phone });
}

export function meApi() {
  return http.get<unknown, UserView>('/users/me');
}

export function menusApi() {
  return http.get<unknown, MenuView[]>('/auth/admin/menus');
}

export function rolesApi() {
  return http.get<unknown, RoleView[]>('/auth/admin/roles');
}


export function employeesApi(params?: { page?: number; pageSize?: number }) {
  return http.get<unknown, PageResult<EmployeeView>>('/auth/admin/employees', { params });
}

export function addEmployeeApi(payload: { phone: string; nickname: string; roleCode: string }) {
  return http.post<unknown, EmployeeView>('/auth/admin/employees', payload);
}

export function changeEmployeeRoleApi(employeeId: number, roleCode: string) {
  return http.put<unknown, EmployeeView>(`/auth/admin/employees/${employeeId}/role`, { roleCode });
}

export function changeEmployeeStatusApi(employeeId: number, status: string) {
  return http.put<unknown, EmployeeView>(`/auth/admin/employees/${employeeId}/status`, { status });
}
