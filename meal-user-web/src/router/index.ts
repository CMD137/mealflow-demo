import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    { path: '/', component: () => import('@/views/HomeView.vue') },
    { path: '/merchant/:merchantId', component: () => import('@/views/MerchantView.vue') },
    { path: '/cart', component: () => import('@/views/CartView.vue') },
    { path: '/addresses', component: () => import('@/views/AddressesView.vue') },
    { path: '/checkout', component: () => import('@/views/CheckoutView.vue') },
    { path: '/order-result', component: () => import('@/views/OrderResultView.vue') },
    { path: '/queue', component: () => import('@/views/QueueView.vue') },
    { path: '/orders', component: () => import('@/views/OrdersView.vue') },
    { path: '/orders/:orderId', component: () => import('@/views/OrderDetailView.vue') },
    { path: '/vouchers', component: () => import('@/views/VouchersView.vue') },
    { path: '/sign', component: () => import('@/views/SignView.vue') },
    { path: '/messages', component: () => import('@/views/MessagesView.vue') },
    { path: '/support', component: () => import('@/views/SupportChatView.vue') },
    { path: '/mine', component: () => import('@/views/MineView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
});

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true;
  }

  const auth = useAuthStore();
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (!auth.user) {
    try {
      await auth.loadProfile();
    } catch {
      auth.logout();
      return { path: '/login', query: { redirect: to.fullPath } };
    }
  }
  return true;
});

export default router;
