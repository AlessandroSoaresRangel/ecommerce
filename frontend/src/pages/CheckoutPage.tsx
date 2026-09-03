import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import * as ordersApi from "../services/orders";
import * as paymentsApi from "../services/payments";
import type { OrderResponse } from "../types/api";
import { useFeedback } from "../hooks/useFeedback";
import { Corners } from "../components/Corners";
import { Tag } from "../components/Tag";
import { formatMoney, orderStatusLabel, orderStatusVariant } from "../utils/format";

export const PENDING_ORDER_KEY = "ecommerce.pendingCheckoutOrderId";

export function CheckoutPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const { reportError } = useFeedback();

  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [startingPayment, setStartingPayment] = useState(false);

  useEffect(() => {
    if (!orderId) return;
    ordersApi.getOrder(Number(orderId)).then(setOrder).catch(reportError);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

  if (!order) return null;

  async function handlePay() {
    setStartingPayment(true);
    try {
      const session = await paymentsApi.createCheckoutSession(order!.id);
      sessionStorage.setItem(PENDING_ORDER_KEY, String(order!.id));
      window.location.href = session.checkoutUrl;
    } catch (err) {
      reportError(err);
      setStartingPayment(false);
    }
  }

  return (
    <div>
      <h6 className="text-muted mb-2">Pedido #{order.id}</h6>
      <h1 className="mb-8">Pagamento</h1>
      <div className="grid gap-8" style={{ gridTemplateColumns: "1fr 420px", alignItems: "start" }}>
        <section className="blueprint p-6">
          <Corners />
          <div className="mb-4 flex items-center justify-between">
            <h4>Itens do pedido</h4>
            <Tag variant={orderStatusVariant(order.status)}>{orderStatusLabel(order.status)}</Tag>
          </div>
          <table className="table">
            <thead>
              <tr>
                <th>Produto</th>
                <th style={{ textAlign: "right" }}>Qtd</th>
                <th style={{ textAlign: "right" }}>Preço travado</th>
              </tr>
            </thead>
            <tbody>
              {order.items.map((i) => (
                <tr key={i.productId}>
                  <td>{i.productName}</td>
                  <td className="font-mono text-[12px]" style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    {i.quantity}
                  </td>
                  <td className="font-mono text-[12px]" style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    {formatMoney(i.unitPriceAtPurchase)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="mt-6 flex items-baseline justify-between">
            <span className="font-heading text-[17px]">Total</span>
            <span className="font-heading text-[26px]">{formatMoney(order.totalAmount)}</span>
          </div>
        </section>

        <aside className="blueprint p-6">
          <Corners />
          <h4 className="mb-3">Stripe Checkout</h4>

          {order.status === "PENDING" ? (
            <>
              <p className="text-[13px] text-muted">
                O backend cria uma Checkout Session e devolve a URL de pagamento. O pedido só vira PAID quando o
                Stripe confirma pelo webhook.
              </p>
              <button className="btn btn-primary btn-block" onClick={handlePay} disabled={startingPayment}>
                Pagar com Stripe
              </button>
              <p className="mt-3 text-[12px] text-muted">
                Cartão de teste 4242 4242 4242 4242, validade futura, qualquer CVC.
              </p>
            </>
          ) : (
            <>
              <div className="mb-4 flex items-center gap-3">
                <Tag variant={orderStatusVariant(order.status)}>{orderStatusLabel(order.status)}</Tag>
              </div>
              <p className="text-[13px] text-muted">
                {order.status === "PAID"
                  ? "Pagamento confirmado pelo webhook do Stripe."
                  : "Este pedido não está mais aguardando pagamento."}
              </p>
              <button className="btn btn-secondary btn-block mt-4" onClick={() => navigate(`/orders/${order.id}`)}>
                Ver pedido
              </button>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}
