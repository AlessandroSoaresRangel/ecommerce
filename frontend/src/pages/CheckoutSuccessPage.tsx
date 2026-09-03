import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import * as ordersApi from "../services/orders";
import type { OrderResponse } from "../types/api";
import { Corners } from "../components/Corners";
import { PENDING_ORDER_KEY } from "./CheckoutPage";

const POLL_INTERVAL_MS = 1500;
const MAX_ATTEMPTS = 20;

/**
 * O Stripe redireciona pra cá depois do pagamento (ver STRIPE_SUCCESS_URL).
 * O pedido só vira PAID quando o webhook do backend processa o evento
 * checkout.session.completed — que pode chegar alguns instantes depois do
 * redirect do navegador — então esta página faz polling em GET /orders/{id}
 * em vez de assumir sucesso na hora.
 */
export function CheckoutSuccessPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const sessionId = searchParams.get("session_id");

  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [timedOut, setTimedOut] = useState(false);
  const attempts = useRef(0);

  useEffect(() => {
    const orderId = sessionStorage.getItem(PENDING_ORDER_KEY);
    if (!orderId) {
      setTimedOut(true);
      return;
    }

    let cancelled = false;
    const poll = async () => {
      try {
        const current = await ordersApi.getOrder(Number(orderId));
        if (cancelled) return;
        setOrder(current);
        if (current.status !== "PENDING") {
          sessionStorage.removeItem(PENDING_ORDER_KEY);
          return;
        }
      } catch {
        // ignora erro transitório e tenta de novo até o limite de tentativas
      }
      attempts.current += 1;
      if (attempts.current >= MAX_ATTEMPTS) {
        if (!cancelled) setTimedOut(true);
        return;
      }
      setTimeout(poll, POLL_INTERVAL_MS);
    };
    poll();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div style={{ maxWidth: 480, margin: "48px auto" }}>
      <div className="blueprint relative p-8 text-center">
        <Corners />
        {order?.status === "PAID" ? (
          <>
            <h4 className="mb-2">Pagamento confirmado</h4>
            <p className="text-[13px] text-muted">
              O webhook do Stripe já processou o pagamento do pedido #{order.id}.
            </p>
            <button className="btn btn-primary mt-4" onClick={() => navigate(`/orders/${order.id}`)}>
              Ver pedido
            </button>
          </>
        ) : timedOut ? (
          <>
            <h4 className="mb-2">Ainda aguardando confirmação</h4>
            <p className="text-[13px] text-muted">
              O Stripe recebeu o pagamento, mas o webhook ainda não confirmou por aqui. Isso é normal se o{" "}
              <code>stripe listen</code> não estiver rodando localmente — confira o pedido em alguns instantes.
            </p>
            <button className="btn btn-secondary mt-4" onClick={() => navigate("/orders")}>
              Ver meus pedidos
            </button>
          </>
        ) : (
          <>
            <h4 className="mb-2">Pagamento recebido pela Stripe</h4>
            <p className="text-[13px] text-muted">
              Aguardando o webhook confirmar o pedido{sessionId ? ` (sessão ${sessionId.slice(0, 20)}…)` : ""}…
            </p>
          </>
        )}
      </div>
    </div>
  );
}
