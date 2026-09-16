import { useEffect, useState } from "react";
import * as ordersApi from "../services/orders";
import type { OrderResponse, OrderStatus } from "../types/api";
import { useFeedback } from "../hooks/useFeedback";
import { Corners } from "../components/Corners";
import { Pagination } from "../components/Pagination";
import { formatDateTime, formatMoney } from "../utils/format";

const PAGE_SIZE = 10;
const STATUSES: OrderStatus[] = ["PENDING", "PAID", "SHIPPED", "CANCELED"];

// Espelha as regras de OrderService.updateStatus: pedido cancelado é final,
// nada volta para PENDING e pedido enviado não volta para PAID.
function isTransitionAllowed(from: OrderStatus, to: OrderStatus): boolean {
  if (from === to) return true;
  if (from === "CANCELED") return false;
  if (to === "PENDING") return false;
  if (from === "SHIPPED" && to === "PAID") return false;
  return true;
}

export function AdminOrdersPage() {
  const { reportError, notify } = useFeedback();

  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [updatingId, setUpdatingId] = useState<number | null>(null);

  const load = () => {
    ordersApi
      .allOrders(page, PAGE_SIZE)
      .then((res) => {
        setOrders(res.content);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      })
      .catch(reportError);
  };

  useEffect(load, [page]); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleStatusChange(order: OrderResponse, status: OrderStatus) {
    if (status === order.status) return;
    setUpdatingId(order.id);
    try {
      const updated = await ordersApi.updateOrderStatus(order.id, status);
      setOrders((prev) => prev.map((o) => (o.id === updated.id ? updated : o)));
      notify(`Status do pedido #${order.id} → ${status}. E-mail disparado para ${order.customerEmail}.`);
    } catch (err) {
      reportError(err);
    } finally {
      setUpdatingId(null);
    }
  }

  return (
    <div>
      <h6 className="text-muted mb-2">Admin</h6>
      <h1 className="mb-8">Pedidos de todos os usuários</h1>

      {orders.length === 0 ? (
        <div className="blueprint relative p-8 text-center">
          <Corners />
          <h4 className="mb-2">Nenhum pedido registrado</h4>
          <p className="m-0 text-[13px] text-muted">Feche um pedido pela loja para gerenciá-lo aqui.</p>
        </div>
      ) : (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Pedido</th>
                <th>Cliente</th>
                <th>Data</th>
                <th style={{ textAlign: "right" }}>Total</th>
                <th style={{ width: 340 }}>Status</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td className="font-mono text-[12px]">#{o.id}</td>
                  <td className="text-[13px]">
                    {o.customerName}
                    <div className="font-mono text-[10.5px] text-muted">{o.customerEmail}</div>
                  </td>
                  <td className="text-[12.5px] text-muted">{formatDateTime(o.createdAt)}</td>
                  <td className="font-mono text-[12px]" style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    {formatMoney(o.totalAmount)}
                  </td>
                  <td>
                    <div className="seg">
                      {STATUSES.map((s) => (
                        <label className="seg-opt" style={{ padding: "5px 9px", fontSize: 11 }} key={s}>
                          <input
                            type="radio"
                            name={`st${o.id}`}
                            checked={o.status === s}
                            disabled={updatingId === o.id || !isTransitionAllowed(o.status, s)}
                            onChange={() => handleStatusChange(o, s)}
                          />
                          <span>{s}</span>
                        </label>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} totalElements={totalElements} size={PAGE_SIZE} onChange={setPage} />
        </>
      )}

      <div className="blueprint relative mt-8 p-6">
        <Corners />
        <div className="mb-2 flex items-center gap-3">
          <h4 className="m-0">Notificações por e-mail</h4>
        </div>
        <p className="m-0 text-[13px] text-muted">
          Toda mudança de status dispara um e-mail para o dono do pedido — em desenvolvimento, veja em{" "}
          <a href="http://localhost:8025" target="_blank" rel="noreferrer">
            localhost:8025
          </a>{" "}
          (Mailpit). É um efeito colateral: se o envio falhar, o erro é só logado, nunca derruba esta operação.
        </p>
      </div>
    </div>
  );
}
