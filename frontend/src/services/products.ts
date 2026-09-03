import { request } from "./client";
import type { MostAccessedProductsResponse, ProductRequest, ProductResponse, SpringPage } from "../types/api";

export interface SearchProductsParams {
  categoryId?: number | null;
  name?: string;
  includeInactive?: boolean;
  page?: number;
  size?: number;
}

// auth fica no default (true): quando um admin está logado o token é
// enviado, e o backend só passa a considerar includeInactive nesse caso —
// para todo mundo mais (anônimo ou CUSTOMER) o parâmetro é ignorado.
export function searchProducts(params: SearchProductsParams = {}): Promise<SpringPage<ProductResponse>> {
  const { categoryId, name, includeInactive, page = 0, size = 20 } = params;
  return request<SpringPage<ProductResponse>>("/products", {
    query: { categoryId, name, includeInactive, page, size },
  });
}

export function mostAccessedProducts(page = 0, size = 20): Promise<MostAccessedProductsResponse> {
  return request<MostAccessedProductsResponse>("/products/most-accessed", { query: { page, size }, auth: false });
}

export function getProduct(id: number): Promise<ProductResponse> {
  return request<ProductResponse>(`/products/${id}`, { auth: false });
}

export function createProduct(payload: ProductRequest): Promise<ProductResponse> {
  return request<ProductResponse>("/products", { method: "POST", body: payload });
}

export function updateProduct(id: number, payload: ProductRequest): Promise<ProductResponse> {
  return request<ProductResponse>(`/products/${id}`, { method: "PUT", body: payload });
}

export function deleteProduct(id: number): Promise<void> {
  return request<void>(`/products/${id}`, { method: "DELETE" });
}
