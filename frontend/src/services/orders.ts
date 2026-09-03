import { request } from "./client";
import type { OrderResponse, OrderStatus, SpringPage } from "../types/api";

export function checkout(): Promise<OrderResponse> {
  return request<OrderResponse>("/orders", { method: "POST" });
}

export function myOrders(page = 0, size = 20): Promise<SpringPage<OrderResponse>> {
  return request<SpringPage<OrderResponse>>("/orders", { query: { page, size } });
}

export function getOrder(id: number): Promise<OrderResponse> {
  return request<OrderResponse>(`/orders/${id}`);
}

export function allOrders(page = 0, size = 20): Promise<SpringPage<OrderResponse>> {
  return request<SpringPage<OrderResponse>>("/admin/orders", { query: { page, size } });
}

export function updateOrderStatus(id: number, status: OrderStatus): Promise<OrderResponse> {
  return request<OrderResponse>(`/admin/orders/${id}/status`, { method: "PATCH", body: { status } });
}
