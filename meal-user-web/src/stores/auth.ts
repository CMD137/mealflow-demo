import { defineStore } from 'pinia';
import { loginApi, meApi } from '@/api/auth';
import { clearToken, readToken, saveToken } from '@/api/http';
import type { LoginRequest, LoginResponse, UserView } from '@/types/api';

interface AuthState {
  token: string;
  loginInfo: LoginResponse | null;
  user: UserView | null;
}

const LOGIN_INFO_KEY = 'mealflow.user.loginInfo';

export const useAuthStore = defineStore('userAuth', {
  state: (): AuthState => ({
    token: readToken() || '',
    loginInfo: readLoginInfo(),
    user: null
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token || state.loginInfo?.token),
    nickname: (state) => state.user?.nickname || state.loginInfo?.nickname || '用户'
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
    logout() {
      this.token = '';
      this.loginInfo = null;
      this.user = null;
      clearToken();
      localStorage.removeItem(LOGIN_INFO_KEY);
    }
  }
});

function readLoginInfo() {
  const raw = localStorage.getItem(LOGIN_INFO_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as LoginResponse;
  } catch {
    return null;
  }
}
