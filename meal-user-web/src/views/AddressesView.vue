<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import { addAddressApi, addressesApi, deleteAddressApi, setDefaultAddressApi, updateAddressApi } from '@/api/auth';
import type { AddressRequest, AddressView } from '@/types/api';

const loading = ref(false);
const saving = ref(false);
const editingId = ref<number | null>(null);
const addresses = ref<AddressView[]>([]);
const errorMessage = ref('');
const pendingDelete = ref<AddressView | null>(null);
const form = reactive<AddressRequest>({ contactName: '', phone: '', detail: '' });

async function load() {
  loading.value = true;
  try {
    addresses.value = await addressesApi();
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  editingId.value = null;
  form.contactName = '';
  form.phone = '';
  form.detail = '';
}

function edit(address: AddressView) {
  editingId.value = address.addressId;
  form.contactName = address.contactName;
  form.phone = address.phone;
  form.detail = address.detail;
}

async function save() {
  if (!form.contactName.trim() || !form.phone.trim() || !form.detail.trim()) {
    errorMessage.value = '请完整填写收货人、联系电话和详细地址';
    return;
  }
  saving.value = true;
  try {
    const wasEmpty = addresses.value.length === 0;
    const saved = editingId.value
      ? await updateAddressApi(editingId.value, form)
      : await addAddressApi(form);
    if (wasEmpty) await setDefaultAddressApi(saved.addressId);
    await load();
    resetForm();
  } finally {
    saving.value = false;
  }
}

async function remove(address: AddressView) {
  pendingDelete.value = address;
}

async function confirmRemove() {
  const address = pendingDelete.value;
  if (!address) return;
  await deleteAddressApi(address.addressId);
  await load();
  if (editingId.value === address.addressId) resetForm();
  pendingDelete.value = null;
}

async function setDefault(address: AddressView) {
  if (address.defaultAddress) return;
  await setDefaultAddressApi(address.addressId);
  await load();
}

onMounted(load);
</script>

<template>
  <AppShell title="收货地址" subtitle="管理下单时使用的配送地址">
    <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>
    <section v-for="address in addresses" :key="address.addressId" class="address-card card">
      <div class="address-main">
        <div>
          <strong>{{ address.contactName }} {{ address.phone }}</strong>
          <p>{{ address.detail }}</p>
        </div>
        <span v-if="address.defaultAddress" class="default-tag">默认</span>
      </div>
      <div class="address-actions">
        <button class="link" :disabled="address.defaultAddress" @click="setDefault(address)">
          {{ address.defaultAddress ? '默认地址' : '设为默认' }}
        </button>
        <button class="link" @click="edit(address)">编辑</button>
        <button class="link danger-link" @click="remove(address)">删除</button>
      </div>
    </section>

    <div v-if="!loading && !addresses.length" class="empty small-empty">还没有收货地址</div>

    <section class="editor card">
      <h2>{{ editingId ? '编辑地址' : '新增地址' }}</h2>
      <label class="field"><span>收货人</span><input v-model.trim="form.contactName" placeholder="姓名" /></label>
      <label class="field"><span>联系电话</span><input v-model.trim="form.phone" inputmode="tel" placeholder="手机号" /></label>
      <label class="field"><span>详细地址</span><textarea v-model.trim="form.detail" placeholder="如：科技园 1 号楼 101 室" /></label>
      <div class="editor-actions">
        <button v-if="editingId" class="ghost-button" @click="resetForm">取消编辑</button>
        <button class="primary-button" :disabled="saving" @click="save">{{ saving ? '保存中...' : '保存地址' }}</button>
      </div>
    </section>
    <section v-if="pendingDelete" class="confirm-card card">
      <p>确认删除“{{ pendingDelete.contactName }}”这条地址吗？</p>
      <div class="editor-actions">
        <button class="ghost-button" @click="pendingDelete = null">取消</button>
        <button class="danger-button" @click="confirmRemove">确认删除</button>
      </div>
    </section>
  </AppShell>
</template>

<style scoped>
.address-card,
.editor {
  margin-bottom: 12px;
  padding: 14px;
}

.inline-error {
  margin: 0 0 12px;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  padding: 10px 12px;
  font-size: 14px;
}

.address-main,
.address-actions,
.editor-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.address-main p {
  margin: 7px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.default-tag {
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 700;
}

.address-actions {
  justify-content: flex-start;
  margin-top: 12px;
}

.address-actions button:disabled {
  color: #94a3b8;
}

.danger-link {
  color: #dc2626;
}

.editor h2 {
  margin: 0 0 14px;
  font-size: 16px;
}

.field span {
  color: #64748b;
  font-size: 13px;
}

.field textarea {
  min-height: 84px;
  resize: vertical;
}

.editor-actions {
  justify-content: flex-end;
}

.small-empty {
  min-height: 100px;
}
</style>
