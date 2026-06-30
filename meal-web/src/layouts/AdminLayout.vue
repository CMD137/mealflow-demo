<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { appMenus, type AppMenuItem } from '@/router/menu';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const visibleMenus = computed(() => appMenus.map(filterMenu).filter(Boolean) as AppMenuItem[]);
const pageTitle = computed(() => (route.meta.title as string) || 'MealFlow');

function filterMenu(item: AppMenuItem): AppMenuItem | null {
  if (item.permission && !auth.hasPermission(item.permission)) {
    return null;
  }
  const children = item.children?.map(filterMenu).filter(Boolean) as AppMenuItem[] | undefined;
  return { ...item, children };
}

function logout() {
  auth.logout();
  router.push('/login');
}
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">M</div>
        <div>
          <div class="brand-title">MealFlow</div>
          <div class="brand-subtitle">管理后台</div>
        </div>
      </div>
      <el-menu :default-active="route.path" router class="side-menu">
        <template v-for="item in visibleMenus" :key="item.path">
          <el-sub-menu v-if="item.children?.length" :index="item.path">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path">
              <el-icon><component :is="child.icon" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <div>
          <h1>{{ pageTitle }}</h1>
          <p>按真实业务流程操作后端服务</p>
        </div>
        <div class="account">
          <el-tag type="info">{{ auth.roleCode }}</el-tag>
          <span>{{ auth.nickname }}</span>
          <span class="merchant">商家 {{ auth.merchantId }}</span>
          <el-button text type="primary" @click="logout">退出</el-button>
        </div>
      </header>
      <main class="main-content">
        <RouterView />
      </main>
    </section>
  </div>
</template>

<style scoped>
.admin-shell {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  width: 100%;
  height: 100%;
}

.sidebar {
  display: flex;
  min-height: 0;
  flex-direction: column;
  border-right: 1px solid #e5e7eb;
  background: #ffffff;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 18px;
  border-bottom: 1px solid #eef2f7;
}

.brand-mark {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 8px;
  background: #1f2937;
  color: #ffffff;
  font-weight: 700;
}

.brand-title {
  font-size: 16px;
  font-weight: 700;
}

.brand-subtitle {
  color: #667085;
  font-size: 12px;
}

.side-menu {
  flex: 1;
  border-right: 0;
}

.workspace {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 24px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}

.topbar h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 650;
}

.topbar p {
  margin: 4px 0 0;
  color: #667085;
  font-size: 12px;
}

.account {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #475467;
  font-size: 14px;
}

.merchant {
  color: #667085;
}

.main-content {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 20px 24px;
}
</style>
