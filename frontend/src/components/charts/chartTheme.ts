export const chartColors = {
  brand: "#2563eb",
  brandLight: "#93c5fd",
  accent: "#059669",
  violet: "#7c3aed",
  amber: "#d97706",
  rose: "#e11d48",
  slate: "#94a3b8",
  grid: "#e2e8f0",
  axis: "#64748b",
} as const;

export const axisProps = {
  tick: { fill: chartColors.axis, fontSize: 11 },
  axisLine: false,
  tickLine: false,
} as const;

export const tooltipStyle = {
  borderRadius: 12,
  border: "1px solid #e2e8f0",
  boxShadow: "0 10px 25px rgba(15, 23, 42, 0.08)",
  fontSize: 12,
  padding: "10px 12px",
} as const;

export const legendStyle = {
  fontSize: 12,
  paddingTop: 8,
} as const;
