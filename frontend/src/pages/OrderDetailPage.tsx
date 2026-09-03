import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import * as ordersApi from "../services/orders";
import type { OrderResponse } from "../types/api";
import { useFeedback } from "../hooks/useFeedback";
import { Corners } from "../components/Corners";
import { Tag } from "../components/Tag";
import { formatDateTime, formatMoney, orderStatusLabel, orderStatusVariant } from "../utils/format";

export function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { reportError } = useFeedback();
  const [order, setOrder] = useState<OrderResponse | null>(null);

  useEffect(() => {
    if (!id) return;
    ordersApi.getOrder(Number(id)).then(setOrder).catch(reportError);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  if (!order) return null;

  return (
    <div>
      <button className="btn btn-ghost mb-6" onClick={() => navigate("/orders")}>
        ← Meus pedidos
      </button>
      <div className="mb-8 flex items-baseline gap-4">
        <h1>Pedido #{order.id}</h1>
        <Tag variant={orderStatusVariant(order.status)}>{orderStatusLabel(order.status)}</Tag>
        <span className="font-mono text-[12px] text-muted">{formatDateTime(order.createdAt)}</span>
      </div>
      <div className="grid gap-8" style={{ gridTemplateColumns: "1fr 372px", alignItems: "start" }}>
        <table className="table">
          <thead>
            <tr>
              <th>Produto</th>
              <th style={{ textAlign: "right" }}>Qtd</th>
              <th style={{ textAlign: "right" }}>unitPriceAtPurchase</th>
              <th style={{ textAlign: "right" }}>Subtotal</th>
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
                <td className="font-mono text-[12px]" style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                  {formatMoney(i.unitPriceAtPurchase * i.quantity)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <aside className="blueprint p-6">
          <Corners />
          <h4 className="mb-4">Total</h4>
          <div className="mb-4 font-heading text-[30px]">{formatMoney(order.totalAmount)}</div>
          <p className="m-0 text-[12px] text-muted">
            O preço unitário fica travado no momento da compra: alterar o produto depois não muda este histórico.
          </p>
          {order.status === "PENDING" && (
            <button className="btn btn-primary btn-block mt-4" onClick={() => navigate(`/checkout/${order.id}`)}>
              Pagar este pedido
            </button>
          )}
        </aside>
      </div>
    </div>
  );
}
