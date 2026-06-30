import { http } from './http';
import type { CategoryView, ImageUploadView, SkuView } from '@/types/api';

export function adminCategoriesApi() {
  return http.get<unknown, CategoryView[]>('/catalog/admin/categories');
}

export function saveCategoryApi(payload: { name: string; sortOrder: number; status: string }, categoryId?: number) {
  if (categoryId) {
    return http.put<unknown, CategoryView>(`/catalog/admin/categories/${categoryId}`, payload);
  }
  return http.post<unknown, CategoryView>('/catalog/admin/categories', payload);
}

export function adminSkusApi() {
  return http.get<unknown, SkuView[]>('/catalog/admin/skus');
}

export function saveSkuApi(
  payload: { categoryId: number; name: string; description?: string; imageUrl?: string; priceCent: number; stock: number; status: string },
  skuId?: number
) {
  if (skuId) {
    return http.put<unknown, SkuView>(`/catalog/admin/skus/${skuId}`, payload);
  }
  return http.post<unknown, SkuView>('/catalog/admin/skus', payload);
}

export function updateSkuStatusApi(skuId: number, status: string) {
  return http.put<unknown, SkuView>(`/catalog/admin/skus/${skuId}/status`, { status });
}

export function updateSkuStockApi(skuId: number, stock: number) {
  return http.put<unknown, SkuView>(`/catalog/admin/skus/${skuId}/stock`, { stock });
}

export function uploadImageApi(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return http.post<unknown, ImageUploadView>('/catalog/admin/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}
