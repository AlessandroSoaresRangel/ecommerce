import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { user, isAdmin, loading } = useAuth();

  if (loading) return null;
  if (!user) return <Navigate to="/auth" replace />;
  // A checagem real fica no backend (hasRole('ADMIN')); isto só evita
  // renderizar a tela pra quem não vai conseguir usá-la.
  if (!isAdmin) return <Navigate to="/" replace />;
  return <>{children}</>;
}
