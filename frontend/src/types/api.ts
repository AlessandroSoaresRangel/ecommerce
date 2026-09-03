// Espelha 1:1 os DTOs em src/main/java/com/seuprojeto/ecommerce/dto/**

export type Role = "ADMIN" | "CUSTOMER";
export type OrderStatus = "PENDING" | "PAID" | "SHIPPED" | "CANCELED";
export type PaymentStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  timestamp: string;
  fieldErrors: Record<string, string> | null;
}

// ---- auth ----
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}
export interface LoginRequest {
  email: string;
  password: string;
}
export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}
export interface RefreshTokenRequest {
  refreshToken: string;
}

// ---- users ----
export interface UserResponse {
  id: number;
  name: string;
  email: string;
  role: Role;
}

// ---- categories ----
export interface Category {
  id: number;
  name: string;
  description: string | null;
}

// ---- products ----
export interface ProductResponse {
  id: number;
  name: string;
  description: string | null;
  price: number;
  stockQuantity: number;
  imageUrl: string | null;
  weightKg: number;
  heightCm: number;
  widthCm: number;
  lengthCm: number;
  active: boolean;
  categoryId: number;
  categoryName: string;
}
export interface ProductRequest {
  name: string;
  description: string | null;
  price: number;
  stockQuantity: number;
  imageUrl: string | null;
  weightKg: number;
  heightCm: number;
  widthCm: number;
  lengthCm: number;
  categoryId: number;
  /** Ignorado na criação (novo produto sempre nasce ativo). Em PUT, omitir/null mantém o estado atual;
   *  é o único jeito de reverter um DELETE (soft delete) e reativar um produto. */
  active?: boolean | null;
}
export interface MostAccessedProductsResponse {
  content: ProductResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// Spring Data's Page<T> JSON shape (returned directly by GET /products, /orders, /admin/orders).
// The current page index lives under `pageable.pageNumber`, not top-level — this app always
// tracks the requested page in local state instead of reading it back, so it's typed but unused.
export interface SpringPage<T> {
  content: T[];
  pageable: { pageNumber: number; pageSize: number };
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ---- cart ----
export interface CartItemResponse {
  id: number;
  productId: number;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}
export interface CartResponse {
  id: number;
  items: CartItemResponse[];
  totalAmount: number;
}
export interface CartItemRequest {
  productId: number;
  quantity: number;
}

// ---- orders ----
export interface OrderItemResponse {
  productId: number;
  productName: string;
  quantity: number;
  unitPriceAtPurchase: number;
}
export interface OrderResponse {
  id: number;
  status: OrderStatus;
  totalAmount: number;
  items: OrderItemResponse[];
  createdAt: string;
  customerName: string;
  customerEmail: string;
}
export interface OrderStatusUpdateRequest {
  status: OrderStatus;
}

// ---- payments ----
export interface PaymentResponse {
  id: number;
  orderId: number;
  method: string;
  status: PaymentStatus;
  transactionId: string | null;
  paidAt: string | null;
}
export interface StripeCheckoutResponse {
  paymentId: number;
  orderId: number;
  checkoutUrl: string;
  sessionId: string;
}

// ---- shipping ----
export interface ShippingOptionResponse {
  carrierName: string;
  serviceName: string;
  price: number;
  deliveryTimeDays: number;
}
export interface ShippingQuoteRequest {
  destinationCep: string;
}
