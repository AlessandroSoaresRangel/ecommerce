interface PaginationProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
  onChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalElements, size, onChange }: PaginationProps) {
  return (
    <div
      className="mt-8 flex items-center gap-3 border-t pt-4"
      style={{ borderColor: "var(--color-divider)" }}
    >
      <button className="btn btn-secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Anterior
      </button>
      <button className="btn btn-secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
        Próxima
      </button>
      <span className="font-mono text-[11px] text-muted">
        page={page} · size={size} · total={totalElements}
      </span>
    </div>
  );
}
