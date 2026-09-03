import { clearTokens, getAccessToken, getRefreshToken, notifyForcedLogout, setTokens } from "./tokenStore";
import type { AuthResponse, ErrorResponse } from "../types/api";

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

// Mensagens de fallback para respostas de erro que não vêm no formato JSON
// de ErrorResponse do GlobalExceptionHandler — principalmente o 401/403
// default do Spring Security para um token ausente/expirado, que nunca passa
// pelo GlobalExceptionHandler.
const FALLBACK_MESSAGES: Record<number, string> = {
  400: "Requisição inválida.",
  401: "Sessão expirada. Faça login novamente.",
  403: "Você não tem permissão para executar esta ação.",
  404: "Recurso não encontrado.",
  409: "Conflito ao processar a operação.",
  415: "Tipo de conteúdo não suportado.",
  500: "Ocorreu um erro inesperado no servidor.",
  502: "Falha ao comunicar com um serviço externo. Tente novamente mais tarde.",
};

export class ApiError extends Error {
  status: number;
  error: string;
  fieldErrors: Record<string, string> | null;

  constructor(status: number, error: string, message: string, fieldErrors: Record<string, string> | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.error = error;
    this.fieldErrors = fieldErrors;
  }
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
  /** false para endpoints públicos (login/registro) — evita tentar refresh num 401 de "senha errada". */
  auth?: boolean;
}

function buildUrl(path: string, query?: RequestOptions["query"]): string {
  const url = new URL(path, BASE_URL);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== "") {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

async function parseErrorBody(res: Response): Promise<ApiError> {
  try {
    const body = (await res.clone().json()) as Partial<ErrorResponse>;
    if (body && typeof body.message === "string") {
      return new ApiError(res.status, body.error ?? res.statusText, body.message, body.fieldErrors ?? null);
    }
  } catch {
    // corpo ausente ou não-JSON — usa a mensagem de fallback abaixo
  }
  return new ApiError(res.status, res.statusText, FALLBACK_MESSAGES[res.status] ?? "Ocorreu um erro inesperado.");
}

let refreshInFlight: Promise<boolean> | null = null;

// Garante um único POST /auth/refresh em voo mesmo se várias requisições
// tomarem 401 ao mesmo tempo (ex.: a página dispara 3 chamadas em paralelo
// com o access token já vencido).
function refreshTokens(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return Promise.resolve(false);

  if (!refreshInFlight) {
    refreshInFlight = fetch(buildUrl("/auth/refresh"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    })
      .then(async (res) => {
        if (!res.ok) return false;
        const auth = (await res.json()) as AuthResponse;
        setTokens(auth.accessToken, auth.refreshToken);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, query, auth = true } = options;

  const doFetch = (): Promise<Response> => {
    const headers: Record<string, string> = {};
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (auth) {
      const token = getAccessToken();
      if (token) headers["Authorization"] = `Bearer ${token}`;
    }
    return fetch(buildUrl(path, query), {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  };

  let res = await doFetch();

  if (res.status === 401 && auth && getAccessToken()) {
    const refreshed = await refreshTokens();
    if (refreshed) {
      res = await doFetch();
    } else {
      clearTokens();
      notifyForcedLogout();
      throw await parseErrorBody(res);
    }
  }

  if (!res.ok) {
    throw await parseErrorBody(res);
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
