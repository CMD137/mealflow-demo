import { skusApi } from '@/api/catalog';
import type { CartItemView, SkuView } from '@/types/api';

export async function buildSkuMapByCart(items: CartItemView[]) {
  const merchantIds = [...new Set(items.map((item) => item.merchantId))];
  const groups = await Promise.all(merchantIds.map((merchantId) => skusApi(merchantId)));
  return Object.fromEntries(groups.flat().map((sku) => [sku.skuId, sku])) as Record<number, SkuView>;
}

export function availableSkus(skus: SkuView[]) {
  return skus.filter((sku) => sku.status === 'ON_SHELF');
}
