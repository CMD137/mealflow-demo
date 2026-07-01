export interface Result<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export interface LoginRequest {
  phone: string;
  code?: string;
}

export interface LoginResponse {
  userId: number;
  token: string;
  nickname: string;
  roleCode: string;
  merchantId?: number | null;
  permissions: string[];
}

export interface UserView {
  userId: number;
  phone: string;
  nickname: string;
  level: string;
}

export interface MerchantView {
  merchantId: number;
  name: string;
  businessStatus: string;
  baseCapacity: number;
  manualFactor: number;
}

export interface CategoryView {
  categoryId: number;
  merchantId: number;
  name: string;
  sortOrder: number;
  status: string;
}

export interface SkuView {
  skuId: number;
  merchantId: number;
  categoryId?: number | null;
  categoryName?: string | null;
  name: string;
  description: string;
  imageUrl: string;
  priceCent: number;
  stock: number;
  status: string;
}

export interface CartItemView {
  cartItemId: number;
  userId: number;
  merchantId: number;
  skuId: number;
  quantity: number;
  selected: boolean;
}

export interface VoucherView {
  voucherId: number;
  name: string;
  type: string;
  discountCent: number;
  stock: number;
  status: string;
}

export interface UserVoucherView {
  userVoucherId: number;
  voucherId: number;
  status: string;
}

export interface SeckillVoucherResponse {
  claimId?: number | null;
  status: 'CLAIMED' | 'DUPLICATE' | 'SOLD_OUT';
  userVoucherId?: number | null;
}

export interface SignInView {
  signedToday: boolean;
  continuousDays: number;
  totalDays: number;
  totalPoints: number;
  todayRewardPoints: number;
  monthSignDates: string[];
}

export interface OrderSkuItem {
  skuId: number;
  quantity: number;
}

export interface SubmitOrderRequest {
  requestId: string;
  merchantId: number;
  addressId?: number | null;
  cartItemIds?: number[] | null;
  items?: OrderSkuItem[] | null;
  userVoucherId?: number | null;
  remark?: string;
}

export interface SubmitOrderResponse {
  mode: 'ORDER_CREATED' | 'QUEUED';
  orderId?: number | null;
  payOrderId?: number | null;
  status?: string | null;
  ticketId?: number | null;
  ticketNo?: string | null;
  aheadCount: number;
  estimatedWaitSeconds: number;
  expireTime?: string | null;
}

export interface OrderItemSnapshot {
  skuId: number;
  name: string;
  priceCent: number;
  quantity: number;
}

export interface OrderView {
  orderId: number;
  userId: number;
  merchantId: number;
  status: string;
  queueTicketId?: number | null;
  capacityTokenId: number;
  payOrderId: number;
  amountCent: number;
  items: OrderItemSnapshot[];
}

export interface PaymentView {
  payOrderId: number;
  orderId: number;
  amountCent: number;
  status: string;
}

export interface MessageView {
  messageId: number;
  userId: number;
  bizType: string;
  content: string;
  createTime: string;
}
