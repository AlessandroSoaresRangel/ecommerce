import { useContext } from "react";
import { FeedbackContext, type FeedbackContextValue } from "../context/FeedbackContext";

export function useFeedback(): FeedbackContextValue {
  const ctx = useContext(FeedbackContext);
  if (!ctx) throw new Error("useFeedback must be used within a FeedbackProvider");
  return ctx;
}
