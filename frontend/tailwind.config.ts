import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        bg: "var(--color-bg)",
        surface: "var(--color-surface)",
        text: "var(--color-text)",
        muted: "var(--color-muted)",
        divider: "var(--color-divider)",
        accent: {
          DEFAULT: "var(--color-accent)",
          100: "var(--color-accent-100)",
          700: "var(--color-accent-700)",
          800: "var(--color-accent-800)",
        },
      },
      fontFamily: {
        heading: "var(--font-heading)",
        body: "var(--font-body)",
        mono: "var(--font-mono)",
      },
      spacing: {
        1: "4px",
        2: "8px",
        3: "12px",
        4: "16px",
        6: "24px",
        8: "32px",
      },
    },
  },
  plugins: [],
} satisfies Config;
