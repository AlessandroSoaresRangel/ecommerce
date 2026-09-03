import { request } from "./client";
import type { Category } from "../types/api";

export function listCategories(): Promise<Category[]> {
  return request<Category[]>("/categories", { auth: false });
}
