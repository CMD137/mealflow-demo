<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from '@/components/AppShell.vue';
import QuantityStepper from '@/components/QuantityStepper.vue';
import { categoriesApi, skusApi } from '@/api/catalog';
import { assetUrl } from '@/api/http';
import { merchantsApi } from '@/api/merchant';
import { useCartStore } from '@/stores/cart';
import { availableSkus } from '@/utils/catalog';
import { formatMoney } from '@/utils/format';
import type { CategoryView, MerchantView, SkuView } from '@/types/api';

const route = useRoute();
const cart = useCartStore();
const merchantId = Number(route.params.merchantId);
const loading = ref(false);
const activeCategory = ref<number | 'all'>('all');
const merchant = ref<MerchantView | null>(null);
const categories = ref<CategoryView[]>([]);
const skus = ref<SkuView[]>([]);
const toast = ref('');
const conflictSku = ref<SkuView | null>(null);
const replacingCart = ref(false);

const visibleSkus = computed(() => {
  const onshelf = availableSkus(skus.value);
  if (activeCategory.value === 'all') return onshelf;
  return onshelf.filter((sku) => sku.categoryId === activeCategory.value);
});

async function load() {
  loading.value = true;
  try {
    const [merchantData, categoryData, skuData] = await Promise.all([
      merchantsApi().then((items) => items.find((item) => item.merchantId === merchantId) || null),
      categoriesApi(merchantId),
      skusApi(merchantId),
      cart.load()
    ]);
    merchant.value = merchantData;
    categories.value = categoryData;
    skus.value = skuData;
    cart.hydrateSkus(skuData);
  } finally {
    loading.value = false;
  }
}

async function addSku(sku: SkuView) {
  try {
    await cart.add(merchantId, sku.skuId, 1);
    showToast(`${sku.name} 已加入购物车`);
  } catch (error) {
    if ((error as { code?: string }).code === 'CART_MERCHANT_CONFLICT') {
      conflictSku.value = sku;
      return;
    }
    showToast((error as Error).message || '加入购物车失败');
  }
}

async function replaceCartAndAdd() {
  const sku = conflictSku.value;
  if (!sku) return;
  replacingCart.value = true;
  try {
    await cart.clear();
    await cart.add(merchantId, sku.skuId, 1);
    conflictSku.value = null;
    showToast(`已切换商户并加入 ${sku.name}`);
  } catch (error) {
    await cart.load();
    showToast(`原购物车已清空，但新商品添加失败：${(error as Error).message || '请重试'}`);
  } finally {
    replacingCart.value = false;
  }
}

async function decreaseSku(sku: SkuView) {
  const item = cart.itemForSku(sku.skuId);
  if (!item) return;
  await cart.update(item.cartItemId, item.quantity - 1);
}

function showToast(message: string) {
  toast.value = message;
  window.setTimeout(() => {
    toast.value = '';
  }, 1600);
}

onMounted(load);
</script>

<template>
  <AppShell :title="merchant?.name || '点餐'" :subtitle="merchant?.businessStatus === 'OPEN' ? '营业中，可以下单' : '商家休息中'">
    <div v-if="toast" class="toast">{{ toast }}</div>

    <section v-if="conflictSku" class="conflict-card card">
      <strong>购物车已有其他商户商品</strong>
      <p>清空现有商品后加入“{{ conflictSku.name }}”吗？</p>
      <div class="conflict-actions">
        <button class="ghost-button" :disabled="replacingCart" @click="conflictSku = null">取消</button>
        <button class="primary-button" :disabled="replacingCart" @click="replaceCartAndAdd">
          {{ replacingCart ? '处理中…' : '清空后添加' }}
        </button>
      </div>
    </section>

    <section class="merchant-banner card">
      <div>
        <span class="status-pill" :class="{ success: merchant?.businessStatus === 'OPEN', danger: merchant?.businessStatus !== 'OPEN' }">
          {{ merchant?.businessStatus === 'OPEN' ? '营业中' : '休息中' }}
        </span>
        <h2>{{ merchant?.name || '加载中' }}</h2>
        <p>高峰期会根据产能进入排队，成单后可跳转支付宝完成支付。</p>
      </div>
    </section>

    <div class="category-strip">
      <button :class="{ active: activeCategory === 'all' }" @click="activeCategory = 'all'">全部</button>
      <button
        v-for="category in categories"
        :key="category.categoryId"
        :class="{ active: activeCategory === category.categoryId }"
        @click="activeCategory = category.categoryId"
      >
        {{ category.name }}
      </button>
    </div>

    <section class="sku-list">
      <article v-for="sku in visibleSkus" :key="sku.skuId" class="sku-row card">
        <div class="sku-image">
          <img v-if="sku.imageUrl" :src="assetUrl(sku.imageUrl)" :alt="sku.name" />
          <span v-else>{{ sku.name.slice(0, 1) }}</span>
        </div>
        <div class="sku-info">
          <h3>{{ sku.name }}</h3>
          <p>{{ sku.description || sku.categoryName || '现做餐品' }}</p>
          <span class="muted">库存 {{ sku.stock }}</span>
          <div class="sku-bottom">
            <strong class="price">{{ formatMoney(sku.priceCent) }}</strong>
            <QuantityStepper
              :quantity="cart.itemForSku(sku.skuId)?.quantity || 0"
              :disabled="merchant?.businessStatus !== 'OPEN' || sku.stock <= 0"
              @increase="addSku(sku)"
              @decrease="decreaseSku(sku)"
            />
          </div>
        </div>
      </article>
    </section>

    <div v-if="!loading && !visibleSkus.length" class="empty">当前类目暂无可售商品</div>

    <div class="cart-bar">
      <div>
        <strong>{{ cart.selectedCount }} 件</strong>
        <span>{{ formatMoney(cart.selectedAmountCent) }}</span>
      </div>
      <RouterLink class="primary-button" to="/checkout">去结算</RouterLink>
    </div>
  </AppShell>
</template>

<style scoped>
.merchant-banner {
  padding: 16px;
}

.merchant-banner h2 {
  margin: 12px 0 6px;
}

.merchant-banner p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.category-strip {
  display: flex;
  gap: 8px;
  margin: 14px -14px 12px;
  overflow-x: auto;
  padding: 0 14px;
}

.category-strip button {
  flex: 0 0 auto;
  border-radius: 999px;
  background: #ffffff;
  color: #64748b;
  padding: 8px 13px;
  font-weight: 700;
}

.category-strip button.active {
  background: #2563eb;
  color: #ffffff;
}

.sku-row {
  display: grid;
  grid-template-columns: 78px 1fr;
  gap: 12px;
  margin-bottom: 10px;
  padding: 12px;
}

.conflict-card {
  margin-bottom: 12px;
  border: 1px solid #fde68a;
  padding: 14px;
}

.conflict-card p {
  margin: 8px 0 12px;
  color: #6b7280;
  font-size: 13px;
}

.conflict-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.sku-list {
  padding-bottom: calc(76px + env(safe-area-inset-bottom));
}

.sku-image {
  display: grid;
  width: 78px;
  height: 78px;
  place-items: center;
  border-radius: 10px;
  background: #eef2ff;
  color: #2563eb;
  font-size: 28px;
  font-weight: 900;
  overflow: hidden;
}

.sku-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sku-info h3 {
  margin: 0 0 5px;
  font-size: 16px;
}

.sku-info p {
  margin: 0 0 5px;
  color: #6b7280;
  font-size: 13px;
}

.sku-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.cart-bar {
  position: fixed;
  right: 14px;
  bottom: 76px;
  left: 14px;
  display: flex;
  max-width: 402px;
  margin: 0 auto;
  align-items: center;
  justify-content: space-between;
  border-radius: 14px;
  background: #111827;
  color: #ffffff;
  padding: 10px 12px 10px 16px;
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.22);
}

.cart-bar span {
  display: block;
  color: #cbd5e1;
  font-size: 13px;
}
</style>
