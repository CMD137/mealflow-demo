import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { addCartItemApi, cartApi, clearCartApi, deleteCartItemApi, selectCartItemApi, updateCartItemApi } from '@/api/cart';
import type { CartItemView, SkuView } from '@/types/api';

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItemView[]>([]);
  const skuMap = ref<Record<number, SkuView>>({});
  const loading = ref(false);

  const selectedItems = computed(() => items.value.filter((item) => item.selected));
  const selectedCount = computed(() => selectedItems.value.reduce((sum, item) => sum + item.quantity, 0));
  const selectedAmountCent = computed(() => selectedItems.value.reduce((sum, item) => {
    const sku = skuMap.value[item.skuId];
    return sum + (sku?.priceCent || 0) * item.quantity;
  }, 0));

  function hydrateSkus(skus: SkuView[]) {
    skuMap.value = {
      ...skuMap.value,
      ...Object.fromEntries(skus.map((sku) => [sku.skuId, sku]))
    };
  }

  async function load() {
    loading.value = true;
    try {
      items.value = await cartApi();
    } finally {
      loading.value = false;
    }
  }

  async function add(merchantId: number, skuId: number, quantity = 1) {
    await addCartItemApi(merchantId, skuId, quantity);
    await load();
  }

  async function update(cartItemId: number, quantity: number) {
    if (quantity <= 0) {
      await deleteCartItemApi(cartItemId);
    } else {
      await updateCartItemApi(cartItemId, quantity);
    }
    await load();
  }

  async function remove(cartItemId: number) {
    await deleteCartItemApi(cartItemId);
    await load();
  }

  async function select(cartItemId: number, selected: boolean) {
    await selectCartItemApi(cartItemId, selected);
    await load();
  }

  async function clear() {
    await clearCartApi();
    items.value = [];
  }

  function itemForSku(skuId: number) {
    return items.value.find((item) => item.skuId === skuId);
  }

  return {
    items,
    skuMap,
    loading,
    selectedItems,
    selectedCount,
    selectedAmountCent,
    hydrateSkus,
    load,
    add,
    update,
    remove,
    select,
    clear,
    itemForSku
  };
});
