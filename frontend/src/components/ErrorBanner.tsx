import { useFeedback } from "../hooks/useFeedback";
import { Corners } from "./Corners";

export function ErrorBanner() {
  const { error, clearError } = useFeedback();
  if (!error) return null;

  return (
    <div
      className="blueprint relative mb-6 p-4"
      style={{ borderColor: "var(--color-accent)", background: "var(--color-accent-100)" }}
    >
      <Corners />
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="mb-2 font-heading text-[17px]" style={{ color: "var(--color-accent-800)" }}>
            {error.status ? `${error.status} · ${error.title}` : error.title}
          </div>
          <p className="m-0 font-mono text-[12px] leading-relaxed" style={{ color: "var(--color-accent-800)" }}>
            {error.message}
          </p>
          {error.fieldErrors && Object.keys(error.fieldErrors).length > 0 && (
            <ul className="m-0 mt-2 pl-4 font-mono text-[11.5px]" style={{ color: "var(--color-accent-800)" }}>
              {Object.entries(error.fieldErrors).map(([field, msg]) => (
                <li key={field}>
                  {field}: {msg}
                </li>
              ))}
            </ul>
          )}
        </div>
        <button className="btn btn-secondary" onClick={clearError}>
          Fechar
        </button>
      </div>
    </div>
  );
}
