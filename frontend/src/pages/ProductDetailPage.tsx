import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import * as productsApi from "../services/products";
import type { ProductResponse } from "../types/api";
import { useAuth } from "../hooks/useAuth";
import { useCart } from "../hooks/useCart";
import { useFeedback } from "../hooks/useFeedback";
import { Corners } from "../components/Corners";
import { ProductImage } from "../components/ProductImage";
import { Tag } from "../components/Tag";
import { formatMoney } from "../utils/format";

export function ProductDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { addItem } = useCart();
  const { reportError, notify } = useFeedback();

  const [product, setProduct] = useState<ProductResponse | null>(null);
  const [qty, setQty] = useState(1);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    if (!id) return;
    productsApi
      .getProduct(Number(id))
      .then(setProduct)
      .catch((err) => {
        reportError(err);
        setProduct(null);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  if (!product) return null;

  async function handleAdd() {
    if (!user) {
      navigate("/auth");
      return;
    }
    setAdding(true);
    try {
      await addItem(product!.id, qty);
      notify(`${product!.name} adicionado ao carrinho.`);
    } catch (err) {
      reportError(err);
    } finally {
      setAdding(false);
    }
  }

  const specs: [string, string][] = [
    ["id", String(product.id)],
    ["categoryId", String(product.categoryId)],
    ["weightKg", Number(product.weightKg || 0).toFixed(3)],
    ["dimensões (A×L×C)", `${product.heightCm} × ${product.widthCm} × ${product.lengthCm} cm`],
    ["active", String(product.active)],
  ];

  return (
    <div>
      <button className="btn btn-ghost mb-6" onClick={() => navigate("/")}>
        ← Catálogo
      </button>
      <div className="grid gap-8" style={{ gridTemplateColumns: "1.1fr 1fr" }}>
        <div className="blueprint relative">
          <Corners />
          <ProductImage imageUrl={product.imageUrl} alt={product.name} fontSize={13} />
        </div>

        <div>
          <div className="mb-3 flex items-center gap-3">
            <span className="card-kicker">{product.categoryName}</span>
            {!product.active && <Tag variant="neutral">produto desativado</Tag>}
          </div>
          <h1 className="mb-3">{product.name}</h1>
          {product.description && <p className="text-muted text-[14px]" style={{ maxWidth: "46ch" }}>{product.description}</p>}

          <div className="my-6 flex items-baseline gap-4">
            <span className="font-heading text-[34px]" style={{ whiteSpace: "nowrap" }}>
              {formatMoney(product.price)}
            </span>
            <span className="font-mono text-[11px] text-muted">stockQuantity {product.stockQuantity}</span>
          </div>

          <div className="mb-6 flex items-stretch gap-3">
            <div className="flex border" style={{ borderColor: "var(--color-divider)" }}>
              <button className="btn btn-ghost" style={{ border: 0, width: 38 }} onClick={() => setQty((q) => Math.max(1, q - 1))}>
                −
              </button>
              <span className="flex items-center justify-center font-heading text-[16px]" style={{ width: 44 }}>
                {qty}
              </span>
              <button className="btn btn-ghost" style={{ border: 0, width: 38 }} onClick={() => setQty((q) => Math.min(Math.max(1, product.stockQuantity), q + 1))}>
                +
              </button>
            </div>
            <button
              className="btn btn-primary flex-1"
              onClick={handleAdd}
              disabled={!product.active || product.stockQuantity < 1 || adding}
            >
              Adicionar ao carrinho
            </button>
          </div>

          <h6 className="mb-3">Ficha técnica</h6>
          <table className="table">
            <tbody>
              {specs.map(([k, v]) => (
                <tr key={k}>
                  <td className="text-muted" style={{ width: "40%", fontSize: 12 }}>
                    {k}
                  </td>
                  <td className="font-mono text-[12px]">{v}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
