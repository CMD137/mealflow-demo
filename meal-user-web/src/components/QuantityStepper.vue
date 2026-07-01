<script setup lang="ts">
defineProps<{
  quantity: number;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  decrease: [];
  increase: [];
}>();
</script>

<template>
  <div class="qty-stepper">
    <button :disabled="disabled || quantity <= 0" @click="emit('decrease')">−</button>
    <span>{{ quantity }}</span>
    <button :disabled="disabled" @click="emit('increase')">＋</button>
  </div>
</template>
