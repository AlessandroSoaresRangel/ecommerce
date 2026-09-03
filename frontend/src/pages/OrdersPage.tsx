import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import * as ordersApi from "../services/orders";
import type { OrderResponse } from "../types/api";
import { useFeedback } from "../hooks/useFeedback";
import { Corners } from "../components/Corners";
import { Tag } from "../components/Tag";
import { Pagination } from "../components/Pagination";
import { formatDateTime, formatMoney, orderStatusLabel, orderStatusVariant } from "../utils/format";

const PAGE_SIZE = 10;

export function OrdersPage() {
  const navigate = useNavigate();
  const { reportError } = useFeedback();

  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  useEffect(() => {
    ordersApi
      .myOrders(page, PAGE_SIZE)
      .then((res) => {
        setOrders(res.content);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      })
      .catch(reportError);
  }, [page, reportError]);

  return (
    <div>
      <h1 className="mb-8">Meus pedidos</h1>
      {orders.length === 0 ? (
        <div className="blueprint relative p-8 text-center">
          <Corners />
          <h4 className="mb-2">Nenhum pedido ainda</h4>
          <p className="m-0 text-[13px] text-muted">Feche um pedido a partir do carrinho para ele aparecer aqui.</p>
        </div>
      ) : (
        <>
          <table className="table">
            <thead>
              <tr>
                <th>Pedido</th>
                <th>Data</th>
                <th>Itens</th>
                <th>Status</th>
                <th style={{ textAlign: "right" }}>Total</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {orders.map((o) => (
                <tr key={o.id}>
                  <td className="font-mono text-[12px]">#{o.id}</td>
                  <td className="text-[12.5px] text-muted">{formatDateTime(o.createdAt)}</td>
                  <td className="text-[12.5px] text-muted">
                    {o.items.reduce((n, i) => n + i.quantity, 0)} itens
                  </td>
                  <td>
                    <Tag variant={orderStatusVariant(o.status)}>{orderStatusLabel(o.status)}</Tag>
                  </td>
                  <td className="font-mono text-[12px]" style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    {formatMoney(o.totalAmount)}
                  </td>
                  <td style={{ textAlign: "right" }}>
                    <button className="btn btn-ghost text-[12px]" onClick={() => navigate(`/orders/${o.id}`)}>
                      detalhe
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} totalElements={totalElements} size={PAGE_SIZE} onChange={setPage} />
        </>
      )}
    </div>
  );
}
