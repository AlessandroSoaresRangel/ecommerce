import { request } from "./client";
import type { ShippingOptionResponse } from "../types/api";

export function quoteShipping(destinationCep: string): Promise<ShippingOptionResponse[]> {
  return request<ShippingOptionResponse[]>("/cart/shipping-quote", {
    method: "POST",
    body: { destinationCep },
  });
}
