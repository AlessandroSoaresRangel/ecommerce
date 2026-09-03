import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import * as productsApi from "../services/products";
import * as categoriesApi from "../services/categories";
import { ApiError } from "../services/client";
import type { Category, ProductRequest, ProductResponse } from "../types/api";
import { useFeedback } from "../hooks/useFeedback";
import { Corners } from "../components/Corners";
import { Tag } from "../components/Tag";
import { Pagination } from "../components/Pagination";
import { formatMoney } from "../utils/format";

const PAGE_SIZE = 8;

type FormState = {
  id?: number;
  name: string;
  description: string;
  price: string;
  stockQuantity: string;
  imageUrl: string;
  weightKg: string;
  heightCm: string;
  widthCm: string;
  lengthCm: string;
  categoryId: string;
};

const EMPTY_FORM: FormState = {
  name: "",
  description: "",
  price: "",
  stockQuantity: "",
  imageUrl: "",
  weightKg: "",
  heightCm: "",
  widthCm: "",
  lengthCm: "",
  categoryId: "",
};

export function AdminProductsPage() {
  const navigate = useNavigate();
  const { reportError, notify } = useFeedback();

  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<ProductResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const [form, setForm] = useState<FormState | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [togglingId, setTogglingId] = useState<number | null>(null);

  useEffect(() => {
    categoriesApi.listCategories().then(setCategories).catch(reportError);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const load = () => {
    productsApi
      .searchProducts({ includeInactive: true, page, size: PAGE_SIZE })
      .then((res) => {
        setProducts(res.content);
        setTotalPages(res.totalPages);
        setTotalElements(res.totalElements);
      })
      .catch(reportError);
  };

  useEffect(load, [page]); // eslint-disable-line react-hooks/exhaustive-deps

  function startCreate() {
    setForm({ ...EMPTY_FORM, categoryId: categories[0] ? String(categories[0].id) : "" });
    setFieldErrors({});
  }

  function startEdit(p: ProductResponse) {
    setForm({
      id: p.id,
      name: p.name,
      description: p.description ?? "",
      price: String(p.price),
      stockQuantity: String(p.stockQuantity),
      imageUrl: p.imageUrl ?? "",
      weightKg: String(p.weightKg),
      heightCm: String(p.heightCm),
      widthCm: String(p.widthCm),
      lengthCm: String(p.lengthCm),
      categoryId: String(p.categoryId),
    });
    setFieldErrors({});
  }

  async function toggleActive(p: ProductResponse) {
    setTogglingId(p.id);
    try {
      const payload: ProductRequest = {
        name: p.name,
        description: p.description,
        price: p.price,
        stockQuantity: p.stockQuantity,
        imageUrl: p.imageUrl,
        weightKg: p.weightKg,
        heightCm: p.heightCm,
        widthCm: p.widthCm,
        lengthCm: p.lengthCm,
        categoryId: p.categoryId,
        active: !p.active,
      };
      await productsApi.updateProduct(p.id, payload);
      notify(`${p.name} ${p.active ? "desativado" : "ativado"} — PUT /products/${p.id}`);
      load();
    } catch (err) {
      reportError(err);
    } finally {
      setTogglingId(null);
    }
  }

  async function handleSave() {
    if (!form) return;
    setFieldErrors({});

    const errors: Record<string, string> = {};
    if (!form.name.trim()) errors.name = "O nome é obrigatório";
    if (form.price === "" || Number(form.price) < 0) errors.price = "O preço não pode ser negativo";
    if (form.stockQuantity === "" || Number(form.stockQuantity) < 0) errors.stockQuantity = "O estoque não pode ser negativo";
    if (!form.weightKg || Number(form.weightKg) <= 0) errors.weightKg = "O peso deve ser maior que zero";
    if (!form.heightCm || Number(form.heightCm) < 1) errors.heightCm = "A altura deve ser no mínimo 1 cm";
    if (!form.widthCm || Number(form.widthCm) < 1) errors.widthCm = "A largura deve ser no mínimo 1 cm";
    if (!form.lengthCm || Number(form.lengthCm) < 1) errors.lengthCm = "O comprimento deve ser no mínimo 1 cm";
    if (!form.categoryId) errors.categoryId = "A categoria é obrigatória";
    if (Object.keys(errors).length) {
      setFieldErrors(errors);
      return;
    }

    const payload: ProductRequest = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      price: Number(form.price),
      stockQuantity: Number(form.stockQuantity),
      imageUrl: form.imageUrl.trim() || null,
      weightKg: Number(form.weightKg),
      heightCm: Number(form.heightCm),
      widthCm: Number(form.widthCm),
      lengthCm: Number(form.lengthCm),
      categoryId: Number(form.categoryId),
    };

    setSaving(true);
    try {
      if (form.id) {
        await productsApi.updateProduct(form.id, payload);
        notify(`PUT /products/${form.id} · produto atualizado.`);
      } else {
        const created = await productsApi.createProduct(payload);
        notify(`201 Created · POST /products · id ${created.id}`);
      }
      setForm(null);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) setFieldErrors(err.fieldErrors);
      else reportError(err);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className="mb-8 flex items-end justify-between">
        <div>
          <h6 className="text-muted mb-2">Admin</h6>
          <h1>Produtos</h1>
        </div>
        <button className="btn btn-primary" onClick={startCreate}>
          Novo produto
        </button>
      </div>

      <div className="grid gap-8" style={{ gridTemplateColumns: "1fr 400px", alignItems: "start" }}>
        <div>
          <table className="table">
            <thead>
              <tr>
                <th style={{ width: 44 }}>ID</th>
                <th style={{ minWidth: 190 }}>Nome</th>
                <th>Categoria</th>
                <th style={{ textAlign: "right" }}>Preço</th>
                <th style={{ textAlign: "right" }}>Estoque</th>
                <th>Ativo</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.id}>
                  <td className="font-mono text-[12px]">{p.id}</td>
                  <td>{p.name}</td>
                  <td className="text-[12.5px] text-muted">{p.categoryName}</td>
                  <td className="font-mono text-[12px]" style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    {formatMoney(p.price)}
                  </td>
                  <td className="font-mono text-[12px]" style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    {p.stockQuantity}
                  </td>
                  <td>
                    <Tag variant={p.active ? "accent" : "neutral"}>{p.active ? "ativo" : "inativo"}</Tag>
                  </td>
                  <td style={{ textAlign: "right", whiteSpace: "nowrap" }}>
                    <button className="btn btn-ghost text-[12px]" onClick={() => startEdit(p)}>
                      editar
                    </button>
                    <button
                      className="btn btn-ghost text-[12px]"
                      onClick={() => toggleActive(p)}
                      disabled={togglingId === p.id}
                    >
                      {p.active ? "desativar" : "ativar"}
                    </button>
                    <button className="btn btn-ghost text-[12px]" onClick={() => navigate(`/products/${p.id}`)}>
                      na loja
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={totalPages} totalElements={totalElements} size={PAGE_SIZE} onChange={setPage} />
        </div>

        {form && (
          <aside className="blueprint sticky p-6" style={{ top: 96 }}>
            <Corners />
            <h4 className="mb-4">{form.id ? `Editar produto ${form.id}` : "Novo produto"}</h4>
            <div className="flex flex-col gap-4">
              <Field label="Nome" value={form.name} error={fieldErrors.name} onChange={(v) => setForm({ ...form, name: v })} />
              <div className="field">
                <label>Descrição</label>
                <textarea
                  className="textarea"
                  rows={3}
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                />
              </div>
              <Field label="Preço" value={form.price} error={fieldErrors.price} onChange={(v) => setForm({ ...form, price: v })} placeholder="129.90" />
              <Field
                label="Estoque"
                value={form.stockQuantity}
                error={fieldErrors.stockQuantity}
                onChange={(v) => setForm({ ...form, stockQuantity: v })}
                placeholder="24"
              />
              <div className="field">
                <label>Categoria</label>
                <select
                  className={`input ${fieldErrors.categoryId ? "has-error" : ""}`}
                  value={form.categoryId}
                  onChange={(e) => setForm({ ...form, categoryId: e.target.value })}
                >
                  <option value="">selecione...</option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.name}
                    </option>
                  ))}
                </select>
                {fieldErrors.categoryId && <ErrorText>{fieldErrors.categoryId}</ErrorText>}
              </div>
              <Field label="URL da imagem" value={form.imageUrl} onChange={(v) => setForm({ ...form, imageUrl: v })} placeholder="/img/1.jpg" />
              <Field label="Peso (kg)" value={form.weightKg} error={fieldErrors.weightKg} onChange={(v) => setForm({ ...form, weightKg: v })} placeholder="0.320" />
              <Field label="Altura (cm)" value={form.heightCm} error={fieldErrors.heightCm} onChange={(v) => setForm({ ...form, heightCm: v })} placeholder="4" />
              <Field label="Largura (cm)" value={form.widthCm} error={fieldErrors.widthCm} onChange={(v) => setForm({ ...form, widthCm: v })} placeholder="30" />
              <Field label="Comprimento (cm)" value={form.lengthCm} error={fieldErrors.lengthCm} onChange={(v) => setForm({ ...form, lengthCm: v })} placeholder="40" />
            </div>
            <div className="mt-6 flex gap-3">
              <button className="btn btn-primary flex-1" onClick={handleSave} disabled={saving}>
                Salvar
              </button>
              <button className="btn btn-secondary" onClick={() => setForm(null)}>
                Cancelar
              </button>
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  error,
  placeholder,
  onChange,
}: {
  label: string;
  value: string;
  error?: string;
  placeholder?: string;
  onChange: (value: string) => void;
}) {
  return (
    <div className="field">
      <label>{label}</label>
      <input
        className={`input ${error ? "has-error" : ""}`}
        type="text"
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {error && <ErrorText>{error}</ErrorText>}
    </div>
  );
}

function ErrorText({ children }: { children: string }) {
  return (
    <div className="mt-1 font-mono text-[11.5px]" style={{ color: "var(--color-accent-800)" }}>
      {children}
    </div>
  );
}
