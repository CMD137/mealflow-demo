import { defineStore } from 'pinia';
import { loginApi, meApi } from '@/api/auth';
import { clearToken, readToken, saveToken } from '@/api/http';
import type { LoginRequest, LoginResponse, UserView } from '@/types/api';

interface AuthState {
  token: string;
  user: UserView | null;
  loginInfo: LoginResponse | null;
}

const LOGIN_INFO_KEY = 'mealflow.loginInfo';

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: readToken() || '',
    user: null,
    loginInfo: readSavedLoginInfo()
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token || state.loginInfo?.token),
    permissions: (state) => state.loginInfo?.permissions || [],
    nickname: (state) => state.user?.nickname || state.loginInfo?.nickname || '未登录',
    roleCode: (state) => state.loginInfo?.roleCode || '-',
    merchantId: (state) => state.loginInfo?.merchantId || 10
  },
  actions: {
    async login(payload: LoginRequest) {
      const info = await loginApi(payload);
      this.loginInfo = info;
      this.token = info.token;
      saveToken(info.token);
      localStorage.setItem(LOGIN_INFO_KEY, JSON.stringify(info));
      await this.loadProfile();
    },
    async loadProfile() {
      if (!this.token && this.loginInfo?.token) {
        this.token = this.loginInfo.token;
        saveToken(this.loginInfo.token);
      }
      if (!this.token) {
        return;
      }
      this.user = await meApi();
    },
    hasPermission(permission: string) {
      return this.permissions.includes(permission);
    },
    logout() {
      this.token = '';
      this.user = null;
      this.loginInfo = null;
      clearToken();
      localStorage.removeItem(LOGIN_INFO_KEY);
    }
  }
});

function readSavedLoginInfo() {
  const raw = localStorage.getItem(LOGIN_INFO_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as LoginResponse;
  } catch {
    return null;
  }
}
