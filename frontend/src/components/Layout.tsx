import type { ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { useCart } from "../hooks/useCart";
import { ErrorBanner } from "./ErrorBanner";
import { Toast } from "./Toast";

const navLinkStyle = {
  fontFamily: "var(--font-heading)",
  fontSize: 14,
  letterSpacing: "0.04em",
  textTransform: "uppercase" as const,
};

export function Layout({ children }: { children: ReactNode }) {
  const { user, isAdmin, logout } = useAuth();
  const { itemCount } = useCart();
  const navigate = useNavigate();

  return (
    <div style={{ minHeight: "100vh", background: "var(--color-bg)", color: "var(--color-text)" }}>
      <div
        className="flex items-center sticky top-0 z-30"
        style={{
          padding: "12px 32px",
          borderBottom: "1px solid var(--color-divider)",
          background: "var(--color-bg)",
          gap: 24,
        }}
      >
        <Link
          to="/"
          style={{ letterSpacing: "0.06em", textTransform: "uppercase", marginRight: 32, color: "var(--color-text)" }}
          className="font-heading text-[15px] font-semibold"
        >
          Oficina
        </Link>
        <nav className="flex items-center" style={{ gap: 24, marginRight: "auto" }}>
          <Link to="/" style={navLinkStyle}>
            Catálogo
          </Link>
          <Link to="/orders" style={navLinkStyle}>
            Pedidos
          </Link>
          {isAdmin && (
            <>
              <Link to="/admin/products" style={navLinkStyle}>
                Admin · Produtos
              </Link>
              <Link to="/admin/orders" style={navLinkStyle}>
                Admin · Pedidos
              </Link>
            </>
          )}
        </nav>
        <button className="btn btn-secondary" onClick={() => navigate("/cart")}>
          Carrinho · {itemCount}
        </button>
        {user ? (
          <button className="btn btn-ghost text-[13px]" onClick={() => navigate("/orders")}>
            {user.name.split(" ")[0]} · {user.role}
          </button>
        ) : (
          <button className="btn btn-ghost text-[13px]" onClick={() => navigate("/auth")}>
            Entrar
          </button>
        )}
        {user && (
          <button className="btn btn-ghost text-[13px]" onClick={logout}>
            Sair
          </button>
        )}
      </div>

      <main style={{ maxWidth: 1280, margin: "0 auto", padding: 32 }}>
        <ErrorBanner />
        <Toast />
        {children}
      </main>

      <footer style={{ borderTop: "1px solid var(--color-divider)", marginTop: 32, padding: "24px 32px" }}>
        <span className="font-mono text-[10.5px] text-muted" style={{ letterSpacing: "0.06em" }}>
          frontend React sobre a API E-commerce · Spring Boot 3.3 · JWT · Stripe Checkout · Melhor Envio
        </span>
      </footer>
    </div>
  );
}
