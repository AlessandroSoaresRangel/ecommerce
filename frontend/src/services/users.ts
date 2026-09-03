import { request } from "./client";
import type { UserResponse } from "../types/api";

export function me(): Promise<UserResponse> {
  return request<UserResponse>("/users/me");
}
