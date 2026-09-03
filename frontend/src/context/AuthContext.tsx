import { createContext, useEffect, useMemo, useState, type ReactNode } from "react";
import * as authApi from "../services/auth";
import * as usersApi from "../services/users";
import { clearTokens, getAccessToken, onForcedLogout, setTokens } from "../services/tokenStore";
import type { UserResponse } from "../types/api";

export interface AuthContextValue {
  user: UserResponse | null;
  loading: boolean;
  isAdmin: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

// Exportado (não só o Provider) para que hooks/useAuth.ts possa consumi-lo via useContext.
export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const loadCurrentUser = async () => {
    if (!getAccessToken()) {
      setUser(null);
      return;
    }
    try {
      const me = await usersApi.me();
      setUser(me);
    } catch {
      clearTokens();
      setUser(null);
    }
  };

  useEffect(() => {
    loadCurrentUser().finally(() => setLoading(false));
    // client.ts chama isso quando um refresh falha no meio de uma requisição
    // (refresh token também expirado) — sem isso a UI continuaria achando
    // que o usuário está logado até a próxima navegação.
    return onForcedLogout(() => setUser(null));
  }, []);

  const login = async (email: string, password: string) => {
    const auth = await authApi.login({ email, password });
    setTokens(auth.accessToken, auth.refreshToken);
    await loadCurrentUser();
  };

  const register = async (name: string, email: string, password: string) => {
    const auth = await authApi.register({ name, email, password });
    setTokens(auth.accessToken, auth.refreshToken);
    await loadCurrentUser();
  };

  const logout = () => {
    clearTokens();
    setUser(null);
  };

  const value = useMemo<AuthContextValue>(
    () => ({ user, loading, isAdmin: user?.role === "ADMIN", login, register, logout }),
    [user, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
