import { createContext, useCallback, useMemo, useState, type ReactNode } from "react";
import { ApiError } from "../services/client";

export interface DisplayError {
  status: number;
  title: string;
  message: string;
  fieldErrors: Record<string, string> | null;
  timestamp: string;
}

export interface FeedbackContextValue {
  error: DisplayError | null;
  toast: string | null;
  reportError: (err: unknown) => DisplayError;
  notify: (text: string) => void;
  clearError: () => void;
  clearToast: () => void;
}

export const FeedbackContext = createContext<FeedbackContextValue | null>(null);

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [error, setError] = useState<DisplayError | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const reportError = useCallback((err: unknown): DisplayError => {
    const display: DisplayError =
      err instanceof ApiError
        ? { status: err.status, title: err.error, message: err.message, fieldErrors: err.fieldErrors, timestamp: new Date().toISOString() }
        : {
            status: 0,
            title: "Erro",
            message: err instanceof Error ? err.message : "Ocorreu um erro inesperado.",
            fieldErrors: null,
            timestamp: new Date().toISOString(),
          };
    setError(display);
    setToast(null);
    return display;
  }, []);

  const notify = useCallback((text: string) => {
    setToast(text);
    setError(null);
  }, []);

  const clearError = useCallback(() => setError(null), []);
  const clearToast = useCallback(() => setToast(null), []);

  const value = useMemo(
    () => ({ error, toast, reportError, notify, clearError, clearToast }),
    [error, toast, reportError, notify, clearError, clearToast],
  );

  return <FeedbackContext.Provider value={value}>{children}</FeedbackContext.Provider>;
}
