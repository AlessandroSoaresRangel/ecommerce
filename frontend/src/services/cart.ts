import { request } from "./client";
import type { CartItemRequest, CartResponse } from "../types/api";

export function getCart(): Promise<CartResponse> {
  return request<CartResponse>("/cart");
}

export function addCartItem(payload: CartItemRequest): Promise<CartResponse> {
  return request<CartResponse>("/cart/items", { method: "POST", body: payload });
}

export function updateCartItemQuantity(itemId: number, quantity: number): Promise<CartResponse> {
  return request<CartResponse>(`/cart/items/${itemId}`, { method: "PUT", query: { quantity } });
}

export function removeCartItem(itemId: number): Promise<CartResponse> {
  return request<CartResponse>(`/cart/items/${itemId}`, { method: "DELETE" });
}
