import { http } from './http';
import type { EmployeeView, LoginRequest, LoginResponse, MenuView, RoleView, UserView } from '@/types/api';

export function loginApi(payload: LoginRequest) {
  return http.post<unknown, LoginResponse>('/auth/login', payload);
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

export function saveRoleApi(payload: { roleCode: string; roleName: string; description?: string; permissions: string[] }) {
  return http.post<unknown, RoleView>('/auth/admin/roles', payload);
}

export function employeesApi() {
  return http.get<unknown, EmployeeView[]>('/auth/admin/employees');
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
