import { request } from "./client";
import type { AuthResponse, LoginRequest, RegisterRequest } from "../types/api";

export function login(payload: LoginRequest): Promise<AuthResponse> {
  return request<AuthResponse>("/auth/login", { method: "POST", body: payload, auth: false });
}

export function register(payload: RegisterRequest): Promise<AuthResponse> {
  return request<AuthResponse>("/auth/register", { method: "POST", body: payload, auth: false });
}
