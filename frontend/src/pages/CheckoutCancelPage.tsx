import { useNavigate } from "react-router-dom";
import { Corners } from "../components/Corners";

export function CheckoutCancelPage() {
  const navigate = useNavigate();
  return (
    <div style={{ maxWidth: 480, margin: "48px auto" }}>
      <div className="blueprint relative p-8 text-center">
        <Corners />
        <h4 className="mb-2">Pagamento cancelado</h4>
        <p className="text-[13px] text-muted">Você pode tentar novamente a partir dos seus pedidos.</p>
        <button className="btn btn-primary mt-4" onClick={() => navigate("/orders")}>
          Ver meus pedidos
        </button>
      </div>
    </div>
  );
}
