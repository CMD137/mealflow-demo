export function formatMoney(value?: number | null) {
  if (value == null) {
    return '-';
  }
  return `￥${(value / 100).toFixed(2)}`;
}

export function statusType(status?: string) {
  if (!status) {
    return 'info';
  }
  if (['ACTIVE', 'ON_SHELF', 'PAID', 'ACCEPTED', 'READY', 'DELIVERED', 'NORMAL'].includes(status)) {
    return 'success';
  }
  if (['WAITING', 'QUEUED', 'PENDING_PAYMENT', 'PROCESSING'].includes(status)) {
    return 'warning';
  }
  if (['OFF_SHELF', 'CANCELLED', 'DISABLED', 'FAILED', 'CLOSED'].includes(status)) {
    return 'danger';
  }
  return 'info';
}
