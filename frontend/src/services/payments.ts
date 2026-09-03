import { request } from "./client";
import type { PaymentResponse, StripeCheckoutResponse } from "../types/api";

export function createCheckoutSession(orderId: number): Promise<StripeCheckoutResponse> {
  return request<StripeCheckoutResponse>(`/orders/${orderId}/payment`, { method: "POST" });
}

export function getPayment(id: number): Promise<PaymentResponse> {
  return request<PaymentResponse>(`/payments/${id}`);
}
