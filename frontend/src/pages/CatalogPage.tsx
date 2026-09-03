import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import * as productsApi from "../services/products";
import * as categoriesApi from "../services/categories";
import type { Category, ProductResponse } from "../types/api";
import { useFeedback } from "../hooks/useFeedback";
import { useAuth } from "../hooks/useAuth";
import { useCart } from "../hooks/useCart";
import { Corners } from "../components/Corners";
import { Pagination } from "../components/Pagination";
import { ProductImage } from "../components/ProductImage";
import { formatMoney } from "../utils/format";

type Sort = "catalog" | "accessed";

const PAGE_SIZE = 6;

export function CatalogPage() {
  const navigate = useNavigate();
  const { reportError } = useFeedback();

  const [categories, setCategories] = useState<Category[]>([]);
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [name, setName] = useState("");
  const [sort, setSort] = useState<Sort>("catalog");
  const [page, setPage] = useState(0);

  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    categoriesApi.listCategories().then(setCategories).catch(reportError);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const load = async () => {
      try {
        if (sort === "accessed") {
          const res = await productsApi.mostAccessedProducts(page, PAGE_SIZE);
          if (cancelled) return;
          setProducts(res.content);
          setTotalPages(res.totalPages);
          setTotalElements(res.totalElements);
        } else {
          const res = await productsApi.searchProducts({ categoryId, name: name || undefined, page, size: PAGE_SIZE });
          if (cancelled) return;
          setProducts(res.content);
          setTotalPages(res.totalPages);
          setTotalElements(res.totalElements);
        }
      } catch (err) {
        if (!cancelled) reportError(err);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => {
      cancelled = true;
    };
  }, [categoryId, name, sort, page, reportError]);

  return (
    <div className="grid gap-8" style={{ gridTemplateColumns: "232px 1fr", alignItems: "start" }}>
      <aside className="sticky flex flex-col gap-6" style={{ top: 96 }}>
        <div className="field">
          <label>Buscar por nome</label>
          <input
            className="input"
            type="text"
            placeholder="camiseta"
            value={name}
            onChange={(e) => {
              setPage(0);
              setName(e.target.value);
            }}
          />
        </div>
        <div>
          <h6 className="mb-3">Categoria</h6>
          <div className="flex flex-col gap-2">
            <label className="radio">
              <input
                type="radio"
                name="cat"
                checked={categoryId === null}
                onChange={() => {
                  setPage(0);
                  setCategoryId(null);
                }}
              />
              <span className="dot" />
              <span>Todas</span>
            </label>
            {categories.map((c) => (
              <label className="radio" key={c.id}>
                <input
                  type="radio"
                  name="cat"
                  checked={categoryId === c.id}
                  onChange={() => {
                    setPage(0);
                    setCategoryId(c.id);
                  }}
                />
                <span className="dot" />
                <span>{c.name}</span>
              </label>
            ))}
          </div>
        </div>
        <div>
          <h6 className="mb-3">Ordenação</h6>
          <div className="seg w-full">
            <label className="seg-opt flex-1 justify-center">
              <input
                type="radio"
                name="sort"
                checked={sort === "catalog"}
                onChange={() => {
                  setPage(0);
                  setSort("catalog");
                }}
              />
              <span>Catálogo</span>
            </label>
            <label className="seg-opt flex-1 justify-center">
              <input
                type="radio"
                name="sort"
                checked={sort === "accessed"}
                onChange={() => {
                  setPage(0);
                  setSort("accessed");
                }}
              />
              <span>Mais vistos</span>
            </label>
          </div>
        </div>
      </aside>

      <section>
        <div className="mb-6 flex items-end justify-between gap-6">
          <div>
            <h6 className="text-muted mb-2">Coleção corrente</h6>
            <h1>Catálogo</h1>
          </div>
          <span className="font-mono text-[11px] text-muted">
            {totalElements} produto{totalElements === 1 ? "" : "s"}
          </span>
        </div>

        <div className="grid gap-8 gap-x-6" style={{ gridTemplateColumns: "repeat(3, 1fr)" }}>
          {products.map((p) => (
            <div className="card blueprint gap-3 p-3" key={p.id}>
              <Corners />
              <div style={{ border: "1px solid var(--color-divider)" }}>
                <ProductImage imageUrl={p.imageUrl} alt={p.name} fontSize={9.5} />
              </div>
              <span className="card-kicker">{p.categoryName}</span>
              <span className="card-title">{p.name}</span>
              <div className="flex items-baseline justify-between gap-2">
                <span className="font-heading text-[21px]">{formatMoney(p.price)}</span>
                <span className="font-mono text-[11px] text-muted">
                  {p.stockQuantity <= 3 ? `restam ${p.stockQuantity}` : `estoque ${p.stockQuantity}`}
                </span>
              </div>
              <div className="flex gap-2">
                <button className="btn btn-secondary flex-1" onClick={() => navigate(`/products/${p.id}`)}>
                  Ver
                </button>
                <AddToCartButton productId={p.id} disabled={!p.active || p.stockQuantity < 1} />
              </div>
            </div>
          ))}
        </div>

        {!loading && products.length === 0 && (
          <div className="blueprint relative p-8 text-center">
            <Corners />
            <h4 className="mb-2">Nenhum produto para este filtro</h4>
            <p className="m-0 text-[13px] text-muted">
              A busca é parcial e case-insensitive no nome, e o filtro por categoria é exato.
            </p>
          </div>
        )}

        <Pagination page={page} totalPages={totalPages} totalElements={totalElements} size={PAGE_SIZE} onChange={setPage} />
      </section>
    </div>
  );
}

function AddToCartButton({ productId, disabled }: { productId: number; disabled?: boolean }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { addItem } = useCart();
  const { reportError, notify } = useFeedback();
  const [adding, setAdding] = useState(false);

  async function handleClick() {
    if (!user) {
      navigate("/auth");
      return;
    }
    setAdding(true);
    try {
      await addItem(productId, 1);
      notify("Produto adicionado ao carrinho.");
    } catch (err) {
      reportError(err);
    } finally {
      setAdding(false);
    }
  }

  return (
    <button className="btn btn-primary flex-1" onClick={handleClick} disabled={disabled || adding}>
      Adicionar
    </button>
  );
}
