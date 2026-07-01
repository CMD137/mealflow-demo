<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import QuantityStepper from '@/components/QuantityStepper.vue';
import { useCartStore } from '@/stores/cart';
import { buildSkuMapByCart } from '@/utils/catalog';
import { formatMoney } from '@/utils/format';

const cart = useCartStore();
const loading = ref(false);

const allSelected = computed(() => cart.items.length > 0 && cart.items.every((item) => item.selected));

async function load() {
  loading.value = true;
  try {
    await cart.load();
    cart.skuMap = await buildSkuMapByCart(cart.items);
  } finally {
    loading.value = false;
  }
}

async function toggleAll() {
  const next = !allSelected.value;
  await Promise.all(cart.items.map((item) => cart.select(item.cartItemId, next)));
  await load();
}

onMounted(load);
</script>

<template>
  <AppShell title="购物车" subtitle="确认本次要结算的商品">
    <div class="cart-actions">
      <button class="ghost-button" :disabled="!cart.items.length" @click="toggleAll">
        {{ allSelected ? '取消全选' : '全选' }}
      </button>
      <button class="danger-button" :disabled="!cart.items.length" @click="cart.clear()">清空</button>
    </div>

    <section v-for="item in cart.items" :key="item.cartItemId" class="cart-item card">
      <label class="check">
        <input type="checkbox" :checked="item.selected" @change="cart.select(item.cartItemId, !item.selected)" />
      </label>
      <div class="thumb">{{ cart.skuMap[item.skuId]?.name?.slice(0, 1) || '餐' }}</div>
      <div>
        <h3>{{ cart.skuMap[item.skuId]?.name || `商品 ${item.skuId}` }}</h3>
        <p>{{ cart.skuMap[item.skuId]?.categoryName || `商家 ${item.merchantId}` }}</p>
        <div class="item-bottom">
          <strong class="price">{{ formatMoney(cart.skuMap[item.skuId]?.priceCent) }}</strong>
          <QuantityStepper
            :quantity="item.quantity"
            @increase="cart.update(item.cartItemId, item.quantity + 1)"
            @decrease="cart.update(item.cartItemId, item.quantity - 1)"
          />
        </div>
      </div>
    </section>

    <div v-if="!loading && !cart.items.length" class="empty">购物车还是空的</div>

    <div class="checkout-bar">
      <div>
        <span>已选 {{ cart.selectedCount }} 件</span>
        <strong>{{ formatMoney(cart.selectedAmountCent) }}</strong>
      </div>
      <RouterLink class="primary-button" to="/checkout">去结算</RouterLink>
    </div>
  </AppShell>
</template>

<style scoped>
.cart-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.cart-item {
  display: grid;
  grid-template-columns: 24px 62px 1fr;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  padding: 12px;
}

.check {
  display: grid;
  place-items: center;
}

.thumb {
  display: grid;
  width: 62px;
  height: 62px;
  place-items: center;
  border-radius: 10px;
  background: #eef2ff;
  color: #2563eb;
  font-size: 24px;
  font-weight: 900;
}

.cart-item h3 {
  margin: 0 0 4px;
  font-size: 15px;
}

.cart-item p {
  margin: 0;
  color: #6b7280;
  font-size: 12px;
}

.item-bottom,
.checkout-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.item-bottom {
  margin-top: 9px;
}

.checkout-bar {
  position: fixed;
  right: 0;
  bottom: 63px;
  left: 0;
  max-width: 430px;
  margin: 0 auto;
  border-top: 1px solid #e5e7eb;
  background: #ffffff;
  padding: 10px 14px;
}

.checkout-bar span {
  display: block;
  color: #6b7280;
  font-size: 12px;
}
</style>
