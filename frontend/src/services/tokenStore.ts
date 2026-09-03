// Fonte única de verdade para os tokens JWT no navegador. Separado de
// client.ts e AuthContext.tsx de propósito: o client precisa ler/gravar
// tokens durante um refresh no meio de uma requisição (sem depender do React),
// e o AuthContext precisa saber quando o client força um logout (refresh
// token também expirado) para atualizar a UI — daí o pub-sub simples abaixo.

const ACCESS_KEY = "ecommerce.accessToken";
const REFRESH_KEY = "ecommerce.refreshToken";

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

export function setTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_KEY, accessToken);
  localStorage.setItem(REFRESH_KEY, refreshToken);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
}

type Listener = () => void;
const forcedLogoutListeners = new Set<Listener>();

export function onForcedLogout(listener: Listener): () => void {
  forcedLogoutListeners.add(listener);
  return () => forcedLogoutListeners.delete(listener);
}

export function notifyForcedLogout(): void {
  forcedLogoutListeners.forEach((listener) => listener());
}
