import { http } from './http';
import type { CartItemView } from '@/types/api';

export function cartApi() {
  return http.get<unknown, CartItemView[]>('/cart');
}

export function addCartItemApi(merchantId: number, skuId: number, quantity: number) {
  return http.post<unknown, CartItemView>('/cart/items', { merchantId, skuId, quantity });
}

export function updateCartItemApi(cartItemId: number, quantity: number) {
  return http.put<unknown, CartItemView>(`/cart/items/${cartItemId}`, { quantity });
}

export function selectCartItemApi(cartItemId: number, selected: boolean) {
  return http.put<unknown, CartItemView>(`/cart/items/${cartItemId}/selected`, { selected });
}

export function deleteCartItemApi(cartItemId: number) {
  return http.delete<unknown, void>(`/cart/items/${cartItemId}`);
}

export function clearCartApi() {
  return http.delete<unknown, number>('/cart');
}
