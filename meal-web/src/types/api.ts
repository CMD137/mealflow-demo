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
  capacityLimit: number;
}

export interface OrderView {
  orderId: number;
  merchantId: number;
  userId: number;
  status: string;
  totalAmountCent: number;
  payableAmountCent: number;
  payOrderId?: number | null;
  queueTicketId?: number | null;
  createTime?: string;
  updateTime?: string;
}

export interface OrderStatisticsView {
  merchantId: number;
  totalCount: number;
  statusCounts: Record<string, number>;
}

export interface QueueTicketView {
  ticketId: number;
  merchantId: number;
  userId: number;
  status: string;
  rank?: number;
  createTime?: string;
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
  merchantId: number;
  name: string;
  type: string;
  discountAmountCent: number;
  stock: number;
  status: string;
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
