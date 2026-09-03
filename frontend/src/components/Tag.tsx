import type { ReactNode } from "react";

type TagVariant = "accent" | "neutral" | "outline";

export function Tag({ children, variant = "neutral" }: { children: ReactNode; variant?: TagVariant }) {
  return <span className={`tag tag-${variant}`}>{children}</span>;
}
