import { createRouter, createWebHistory } from 'vue-router';
import AdminLayout from '@/layouts/AdminLayout.vue';
import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: () => import('@/views/login/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: AdminLayout,
      children: [
        { path: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue'), meta: { title: '工作台' } },
        { path: 'merchant/profile', component: () => import('@/views/merchant/MerchantProfileView.vue'), meta: { title: '商家资料', permission: 'MERCHANT_MANAGE' } },
        { path: 'merchant/capacity', component: () => import('@/views/merchant/CapacityView.vue'), meta: { title: '产能配置', permission: 'MERCHANT_MANAGE' } },
        { path: 'catalog/categories', component: () => import('@/views/catalog/CategoriesView.vue'), meta: { title: '商品类目', permission: 'CATALOG_MANAGE' } },
        { path: 'catalog/skus', component: () => import('@/views/catalog/SkusView.vue'), meta: { title: '商品管理', permission: 'CATALOG_MANAGE' } },
        { path: 'orders', component: () => import('@/views/orders/OrdersView.vue'), meta: { title: '订单管理', permission: 'ORDER_WRITE' } },
        { path: 'fulfillment', component: () => import('@/views/fulfillment/FulfillmentView.vue'), meta: { title: '履约工作台', permission: 'FULFILLMENT_OPERATE' } },
        { path: 'queue', component: () => import('@/views/queue/QueueView.vue'), meta: { title: '排队与产能', permission: 'MERCHANT_MANAGE' } },
        { path: 'promotion/vouchers', component: () => import('@/views/promotion/VouchersView.vue'), meta: { title: '优惠券管理', permission: 'MERCHANT_MANAGE' } },
        { path: 'promotion/wallet', component: () => import('@/views/promotion/WalletView.vue'), meta: { title: '用户券包', permission: 'VOUCHER_USE' } },
        { path: 'employees', component: () => import('@/views/access/EmployeesView.vue'), meta: { title: '员工管理', permission: 'MERCHANT_MANAGE' } },
        { path: 'roles', component: () => import('@/views/access/RolesView.vue'), meta: { title: '角色权限', permission: 'MERCHANT_MANAGE' } },
        { path: 'notify/messages', component: () => import('@/views/notify/NotifyView.vue'), meta: { title: '通知中心', permission: 'NOTIFY_READ' } },
        { path: 'ops/events', component: () => import('@/views/ops/EventsView.vue'), meta: { title: '事件运维', permission: 'INTERNAL_OPERATE' } }
      ]
    }
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
  const permission = to.meta.permission as string | undefined;
  if (permission && !auth.hasPermission(permission)) {
    return '/dashboard';
  }
  return true;
});

export default router;
