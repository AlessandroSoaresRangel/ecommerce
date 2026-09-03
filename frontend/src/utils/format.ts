const currencyFormatter = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dateTimeFormatter = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

export function formatMoney(value: number): string {
  return currencyFormatter.format(value);
}

export function formatDateTime(isoString: string): string {
  return dateTimeFormatter.format(new Date(isoString));
}

export function formatCep(value: string): string {
  const digits = value.replace(/\D/g, "").slice(0, 8);
  if (digits.length <= 5) return digits;
  return `${digits.slice(0, 5)}-${digits.slice(5)}`;
}

const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: "Pendente",
  PAID: "Pago",
  SHIPPED: "Enviado",
  CANCELED: "Cancelado",
};

export function orderStatusLabel(status: string): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

export type TagVariant = "accent" | "neutral" | "outline";

export function orderStatusVariant(status: string): TagVariant {
  switch (status) {
    case "PAID":
      return "accent";
    case "SHIPPED":
      return "outline";
    default:
      return "neutral";
  }
}
