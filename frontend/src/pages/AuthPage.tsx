import { useState, type FormEvent } from "react";
import { useLocation, useNavigate, type Location } from "react-router-dom";
import { useAuth } from "../hooks/useAuth";
import { Corners } from "../components/Corners";
import { ApiError } from "../services/client";

type Mode = "login" | "register";

export function AuthPage() {
  const { login, register, user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [mode, setMode] = useState<Mode>("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const from = (location.state as { from?: Location })?.from;

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFieldErrors({});

    const errors: Record<string, string> = {};
    if (mode === "register" && !name.trim()) errors.name = "O nome é obrigatório";
    if (!email.trim()) errors.email = "O e-mail é obrigatório";
    if (!password) errors.password = "A senha é obrigatória";
    else if (mode === "register" && password.length < 8) errors.password = "A senha deve ter no mínimo 8 caracteres";
    if (Object.keys(errors).length) {
      setFieldErrors(errors);
      return;
    }

    setSubmitting(true);
    try {
      if (mode === "register") await register(name.trim(), email.trim(), password);
      else await login(email.trim(), password);
      navigate(from?.pathname ?? "/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors);
      else if (err instanceof ApiError && err.status === 409) setFieldErrors({ email: err.message });
      else if (err instanceof ApiError) setFieldErrors({ password: err.message });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ maxWidth: 420, margin: "32px auto" }}>
      <div className="seg mb-6 w-full">
        <label className="seg-opt flex-1 justify-center">
          <input type="radio" checked={mode === "login"} onChange={() => setMode("login")} />
          <span>Entrar</span>
        </label>
        <label className="seg-opt flex-1 justify-center">
          <input type="radio" checked={mode === "register"} onChange={() => setMode("register")} />
          <span>Criar conta</span>
        </label>
      </div>

      <div className="blueprint relative p-6">
        <Corners />
        <h3 className="mb-6">{mode === "register" ? "Criar conta" : "Entrar"}</h3>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {mode === "register" && (
            <div className="field">
              <label>Nome</label>
              <input
                className={`input ${fieldErrors.name ? "has-error" : ""}`}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Seu nome"
              />
              {fieldErrors.name && (
                <div className="mt-1 font-mono text-[11.5px]" style={{ color: "var(--color-accent-800)" }}>
                  {fieldErrors.name}
                </div>
              )}
            </div>
          )}
          <div className="field">
            <label>E-mail</label>
            <input
              className={`input ${fieldErrors.email ? "has-error" : ""}`}
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="voce@exemplo.com"
            />
            {fieldErrors.email && (
              <div className="mt-1 font-mono text-[11.5px]" style={{ color: "var(--color-accent-800)" }}>
                {fieldErrors.email}
              </div>
            )}
          </div>
          <div className="field">
            <label>Senha</label>
            <input
              className={`input ${fieldErrors.password ? "has-error" : ""}`}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={mode === "register" ? "mínimo 8 caracteres" : "••••••••"}
            />
            {fieldErrors.password && (
              <div className="mt-1 font-mono text-[11.5px]" style={{ color: "var(--color-accent-800)" }}>
                {fieldErrors.password}
              </div>
            )}
          </div>
          <button className="btn btn-primary btn-block mt-2" type="submit" disabled={submitting}>
            {mode === "register" ? "Criar conta" : "Entrar"}
          </button>
        </form>
        <p className="mt-4 mb-0 font-mono text-[11.5px] text-muted">
          devolve accessToken + refreshToken · todo cadastro novo sai como CUSTOMER com um carrinho vazio
        </p>
      </div>

      {user && (
        <div className="blueprint relative mt-6 flex items-center justify-between gap-4 p-4">
          <Corners />
          <div>
            <div className="font-heading text-[16px]">{user.name}</div>
            <span className="font-mono text-[11px] text-muted">
              {user.email} · role {user.role}
            </span>
          </div>
          <button className="btn btn-ghost" onClick={logout}>
            Sair
          </button>
        </div>
      )}
    </div>
  );
}
