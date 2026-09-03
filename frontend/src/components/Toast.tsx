import { useFeedback } from "../hooks/useFeedback";

export function Toast() {
  const { toast, error, clearToast } = useFeedback();
  if (!toast || error) return null;

  return (
    <div
      className="mb-6 flex items-center justify-between gap-4 border px-4 py-3"
      style={{ borderColor: "var(--color-divider)" }}
    >
      <span className="text-[13px]">{toast}</span>
      <button className="btn btn-ghost text-[12px]" onClick={clearToast}>
        ok
      </button>
    </div>
  );
}
