export function formatMoney(cent?: number | null) {
  return `￥${((cent || 0) / 100).toFixed(2)}`;
}

export function formatWait(seconds?: number | null) {
  if (!seconds) {
    return '很快';
  }
  const minutes = Math.ceil(seconds / 60);
  return `${minutes} 分钟`;
}

export function orderStatusText(status?: string | null) {
  const map: Record<string, string> = {
    PENDING_PAYMENT: '待支付',
    PAID: '已支付',
    WAIT_MERCHANT_ACCEPT: '待接单',
    MERCHANT_ACCEPTED: '已接单',
    COOKING: '制作中',
    WAIT_RIDER_PICKUP: '待取餐',
    DELIVERING: '配送中',
    COMPLETED: '已完成',
    CANCELLED: '已取消'
  };
  return status ? map[status] || status : '-';
}

export function statusClass(status?: string | null) {
  if (!status) return 'muted';
  if (['PENDING_PAYMENT', 'WAIT_MERCHANT_ACCEPT'].includes(status)) return 'warning';
  if (['CANCELLED'].includes(status)) return 'danger';
  if (['COMPLETED', 'DELIVERED', 'PAID'].includes(status)) return 'success';
  return 'primary';
}
