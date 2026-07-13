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

export interface MenuView {
  id: number;
  parentId?: number | null;
  menuCode: string;
  menuName: string;
  path: string;
  permissionCode: string;
  sortOrder: number;
  visible: boolean;
}

export interface LoginResponse {
  userId: number;
  token: string;
  nickname: string;
  roleCode: string;
  merchantId?: number | null;
  permissions: string[];
  menus: MenuView[];
}

export interface UserView {
  userId: number;
  phone: string;
  nickname: string;
  level: string;
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
  categoryId: number;
  categoryName?: string;
  name: string;
  description?: string;
  imageUrl?: string;
  priceCent: number;
  stock: number;
  status: string;
}

export interface ImageUploadView {
  url: string;
  objectKey: string;
  provider: string;
  size: number;
  contentType: string;
}

export interface MerchantView {
  merchantId: number;
  name: string;
  businessStatus: string;
  baseCapacity: number;
  manualFactor: number;
}

export interface OrderView {
  orderId: number;
  merchantId: number;
  userId: number;
  status: string;
  amountCent: number;
  payOrderId: number;
  queueTicketId?: number | null;
  capacityTokenId: number;
  items: OrderItemSnapshot[];
}

export interface OrderItemSnapshot {
  skuId: number;
  skuName: string;
  priceCent: number;
  quantity: number;
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
  items: OrderSkuItem[];
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

export interface OrderStatisticsView {
  totalCount: number;
  waitingAcceptCount: number;
  acceptedCount: number;
  deliveringCount: number;
  completedCount: number;
  cancelledCount: number;
  turnoverCent: number;
}

export interface QueueTicketView {
  ticketId: number;
  ticketNo: string;
  status: string;
  aheadCount: number;
  estimatedWaitSeconds: number;
  expireTime?: string;
  canCancel: boolean;
}

export interface CapacityTokenView {
  capacityTokenId: number;
  merchantId: number;
  orderId?: number | null;
  ticketId?: number | null;
  status: string;
  createTime?: string;
}

export interface VoucherView {
  voucherId: number;
  name: string;
  type: string;
  discountCent: number;
  stock: number;
  status: string;
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
  createTime?: string;
}

export interface DeliveryView {
  deliveryId: number;
  messageId: number;
  userId: number;
  channel: string;
  target: string;
  status: string;
  content: string;
  createTime?: string;
}

export interface ConsumerRecordView {
  id: number;
  eventKey: string;
  consumerGroup: string;
  eventType: string;
  status: string;
  lastError?: string;
  createTime?: string;
  updateTime?: string;
}

export interface LocalEventView {
  id: number;
  eventKey: string;
  eventType: string;
  eventVersion: number;
  aggregateType: string;
  aggregateId: number;
  payloadJson: string;
  status: string;
  retryCount: number;
  lastError?: string;
  createTime?: string;
  updateTime?: string;
}

export interface RoleView {
  roleCode: string;
  roleName: string;
  description?: string;
  builtin: boolean;
  permissions?: string[];
}

export interface EmployeeView {
  employeeId: number;
  merchantId: number;
  userId: number;
  phone: string;
  nickname: string;
  roleCode: string;
  status: string;
}
