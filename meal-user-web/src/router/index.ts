import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    { path: '/', component: () => import('@/views/HomeView.vue') },
    { path: '/merchant/:merchantId', component: () => import('@/views/MerchantView.vue') },
    { path: '/cart', component: () => import('@/views/CartView.vue') },
    { path: '/checkout', component: () => import('@/views/CheckoutView.vue') },
    { path: '/order-result', component: () => import('@/views/OrderResultView.vue') },
    { path: '/orders', component: () => import('@/views/OrdersView.vue') },
    { path: '/orders/:orderId', component: () => import('@/views/OrderDetailView.vue') },
    { path: '/vouchers', component: () => import('@/views/VouchersView.vue') },
    { path: '/sign', component: () => import('@/views/SignView.vue') },
    { path: '/messages', component: () => import('@/views/MessagesView.vue') },
    { path: '/mine', component: () => import('@/views/MineView.vue') }
  ]
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.meta.public) {
    return true;
  }
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (!auth.user) {
    await auth.loadProfile();
  }
  return true;
});

export default router;
