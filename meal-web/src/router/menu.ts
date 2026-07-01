import type { Component } from 'vue';
import {
  Bell,
  DataAnalysis,
  Discount,
  Goods,
  List,
  Management,
  Monitor,
  Operation,
  Shop,
  Tickets,
  User
} from '@element-plus/icons-vue';

export interface AppMenuItem {
  path: string;
  title: string;
  icon: Component;
  permission?: string;
  children?: AppMenuItem[];
}

export const appMenus: AppMenuItem[] = [
  { path: '/dashboard', title: '工作台', icon: DataAnalysis, permission: 'MERCHANT_MANAGE' },
  {
    path: '/merchant',
    title: '商家管理',
    icon: Shop,
    permission: 'MERCHANT_MANAGE',
    children: [
      { path: '/merchant/profile', title: '商家资料', icon: Shop, permission: 'MERCHANT_MANAGE' },
      { path: '/merchant/capacity', title: '产能配置', icon: Operation, permission: 'MERCHANT_MANAGE' }
    ]
  },
  {
    path: '/catalog',
    title: '商品中心',
    icon: Goods,
    permission: 'CATALOG_MANAGE',
    children: [
      { path: '/catalog/categories', title: '商品类目', icon: List, permission: 'CATALOG_MANAGE' },
      { path: '/catalog/skus', title: '商品管理', icon: Goods, permission: 'CATALOG_MANAGE' }
    ]
  },
  { path: '/orders', title: '订单管理', icon: Tickets, permission: 'ORDER_WRITE' },
  { path: '/fulfillment', title: '履约工作台', icon: Management, permission: 'FULFILLMENT_OPERATE' },
  { path: '/queue', title: '排队与产能', icon: Monitor, permission: 'INTERNAL_OPERATE' },
  {
    path: '/promotion',
    title: '优惠券',
    icon: Discount,
    permission: 'MERCHANT_MANAGE',
    children: [
      { path: '/promotion/vouchers', title: '优惠券管理', icon: Discount, permission: 'MERCHANT_MANAGE' },
      { path: '/promotion/wallet', title: '用户券包', icon: Tickets, permission: 'VOUCHER_USE' }
    ]
  },
  {
    path: '/access',
    title: '员工权限',
    icon: User,
    permission: 'MERCHANT_MANAGE',
    children: [
      { path: '/employees', title: '员工管理', icon: User, permission: 'MERCHANT_MANAGE' },
      { path: '/roles', title: '角色权限', icon: Management, permission: 'MERCHANT_MANAGE' }
    ]
  },
  { path: '/notify/messages', title: '通知中心', icon: Bell, permission: 'NOTIFY_READ' },
  { path: '/ops/events', title: '事件运维', icon: Operation, permission: 'INTERNAL_OPERATE' }
];
