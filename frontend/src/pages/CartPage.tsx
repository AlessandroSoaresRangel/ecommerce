import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../services/client";
import * as ordersApi from "../services/orders";
import * as shippingApi from "../services/shipping";
import type { ShippingOptionResponse } from "../types/api";
import { useCart } from "../hooks/useCart";
import { useFeedback } from "../hooks/useFeedback";
import { Corners } from "../components/Corners";
import { formatCep, formatMoney } from "../utils/format";

export function CartPage() {
  const navigate = useNavigate();
  const { cart, updateItemQuantity, removeItem, refresh } = useCart();
  const { reportError, notify } = useFeedback();

  const [cep, setCep] = useState("");
  const [options, setOptions] = useState<ShippingOptionResponse[] | null>(null);
  const [shipError, setShipError] = useState<string | null>(null);
  const [shipIdx, setShipIdx] = useState<number | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [checkingOut, setCheckingOut] = useState(false);

  if (!cart) return null;

  const cartEmpty = cart.items.length === 0;

  async function handleQuote() {
    setShipError(null);
    setOptions(null);
    setShipIdx(null);
    setQuoting(true);
    try {
      const res = await shippingApi.quoteShipping(cep);
      setOptions(res);
      if (res.length > 0) setShipIdx(0);
    } catch (err) {
      const message =
        err instanceof ApiError
          ? `${err.status} · ${err.message}`
          : "Não foi possível calcular o frete.";
      setShipError(message);
    } finally {
      setQuoting(false);
    }
  }

  async function handleCheckout() {
    setCheckingOut(true);
    try {
      const order = await ordersApi.checkout();
      await refresh();
      notify("Pedido criado. Prossiga para o pagamento.");
      navigate(`/checkout/${order.id}`);
    } catch (err) {
      reportError(err);
    } finally {
      setCheckingOut(false);
    }
  }

  const shipCost = options && shipIdx != null ? options[shipIdx].price : 0;

  return (
    <div>
      <h1 className="mb-8">Carrinho</h1>

      {cartEmpty ? (
        <div className="blueprint relative p-8 text-center">
          <Corners />
          <h4 className="mb-2">Seu carrinho está vazio</h4>
          <p className="text-[13px] text-muted">Um carrinho vazio recusa o checkout com 400 · EmptyCartException.</p>
          <button className="btn btn-primary mt-4" onClick={() => navigate("/")}>
            Ver catálogo
          </button>
        </div>
      ) : (
        <div className="grid gap-8" style={{ gridTemplateColumns: "1fr 372px", alignItems: "start" }}>
          <section>
            <table className="table">
              <thead>
                <tr>
                  <th style={{ minWidth: 220 }}>Produto</th>
                  <th style={{ width: 110 }}>Qtd</th>
                  <th style={{ textAlign: "right" }}>Unitário</th>
                  <th style={{ textAlign: "right" }}>Subtotal</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {cart.items.map((i) => (
                  <tr key={i.id}>
                    <td>
                      <div className="font-heading text-[16px]">{i.productName}</div>
                      <span className="font-mono text-[10.5px] text-muted" style={{ whiteSpace: "nowrap" }}>
                        productId {i.productId}
                      </span>
                    </td>
                    <td>
                      <div className="inline-flex border" style={{ borderColor: "var(--color-divider)" }}>
                        <button
                          className="btn btn-ghost"
                          style={{ border: 0, width: 30 }}
                          onClick={() =>
                            i.quantity > 1
                              ? updateItemQuantity(i.id, i.quantity - 1).catch(reportError)
                              : removeItem(i.id).catch(reportError)
                          }
                        >
                          −
                        </button>
                        <span className="flex items-center justify-center text-[13px]" style={{ width: 32 }}>
                          {i.quantity}
                        </span>
                        <button
                          className="btn btn-ghost"
                          style={{ border: 0, width: 30 }}
                          onClick={() => updateItemQuantity(i.id, i.quantity + 1).catch(reportError)}
                        >
                          +
                        </button>
                      </div>
                    </td>
                    <td className="font-mono text-[12px]" style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                      {formatMoney(i.unitPrice)}
                    </td>
                    <td className="font-mono text-[12px]" style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                      {formatMoney(i.subtotal)}
                    </td>
                    <td style={{ textAlign: "right" }}>
                      <button className="btn btn-ghost text-[12px]" onClick={() => removeItem(i.id).catch(reportError)}>
                        remover
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="blueprint relative mt-8 p-6">
              <Corners />
              <h4 className="mb-4">Frete (estimativa)</h4>
              <div className="mb-4 flex items-end gap-3">
                <div className="field" style={{ width: 180 }}>
                  <label>CEP de destino</label>
                  <input
                    className="input"
                    type="text"
                    placeholder="20040-020"
                    value={cep}
                    onChange={(e) => setCep(formatCep(e.target.value))}
                  />
                </div>
                <button className="btn btn-secondary" onClick={handleQuote} disabled={quoting}>
                  Calcular
                </button>
              </div>
              <p className="mb-4 font-mono text-[11.5px] text-muted">
                calculado a partir do peso e das dimensões dos itens · não faz parte do total do pedido — o
                checkout sempre cobra só os itens
              </p>

              {options && options.length > 0 && (
                <div className="flex flex-col gap-2">
                  {options.map((o, idx) => (
                    <label
                      className="radio justify-between border p-3"
                      style={{ borderColor: "var(--color-divider)", width: "100%" }}
                      key={`${o.carrierName}-${o.serviceName}`}
                    >
                      <span className="inline-flex items-center gap-2">
                        <input type="radio" name="ship" checked={shipIdx === idx} onChange={() => setShipIdx(idx)} />
                        <span className="dot" />
                        <span className="font-heading text-[15px]">
                          {o.carrierName} · {o.serviceName}
                        </span>
                      </span>
                      <span className="inline-flex items-center gap-4 font-mono text-[12px]">
                        <span className="text-muted">{o.deliveryTimeDays} dias</span>
                        <span>{formatMoney(o.price)}</span>
                      </span>
                    </label>
                  ))}
                </div>
              )}

              {shipError && (
                <p className="m-0 text-[13px]" style={{ color: "var(--color-accent-800)" }}>
                  {shipError}
                </p>
              )}
            </div>
          </section>

          <aside className="blueprint sticky p-6" style={{ top: 96 }}>
            <Corners />
            <h4 className="mb-4">Resumo</h4>
            <div className="flex flex-col gap-3 text-[13px]">
              <div className="flex justify-between">
                <span className="text-muted">Itens</span>
                <span className="font-mono">{formatMoney(cart.totalAmount)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted">Frete (estimado)</span>
                <span className="font-mono">{options && shipIdx != null ? formatMoney(shipCost) : "a calcular"}</span>
              </div>
              <div style={{ height: 1, background: "var(--color-divider)" }} />
              <div className="flex items-baseline justify-between">
                <span className="font-heading text-[17px]">Total do pedido</span>
                <span className="font-heading text-[26px]">{formatMoney(cart.totalAmount)}</span>
              </div>
            </div>
            <button className="btn btn-primary btn-block mt-6" onClick={handleCheckout} disabled={checkingOut}>
              Fechar pedido
            </button>
            <p className="m-0 mt-3 text-[11px] text-muted">
              Verifica e debita o estoque, congela o preço de compra e esvazia o carrinho numa única transação. O
              frete não entra no total — é só uma estimativa de custo de envio.
            </p>
          </aside>
        </div>
      )}
    </div>
  );
}
