import { http } from './http';
import type { CategoryView, SkuView } from '@/types/api';

export function categoriesApi(merchantId: number) {
  return http.get<unknown, CategoryView[]>(`/catalog/merchants/${merchantId}/categories`);
}

export function skusApi(merchantId: number) {
  return http.get<unknown, SkuView[]>(`/catalog/merchants/${merchantId}/skus`);
}
