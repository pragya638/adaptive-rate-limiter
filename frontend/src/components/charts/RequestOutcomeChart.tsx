import {
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
} from "recharts";

import type { TrafficMetrics } from "../../api/types";
import { formatInteger, formatPercent } from "../../lib/format";
import { ChartDataTable } from "./ChartDataTable";
import { ChartEmpty } from "./ChartEmpty";
import { chartColors, legendStyle, tooltipStyle } from "./chartTheme";

export function RequestOutcomeChart({
  metrics,
}: {
  metrics: TrafficMetrics | undefined;
}) {
  const total = metrics?.totalRequests ?? 0;

  if (!metrics || total === 0) {
    return (
      <ChartEmpty
        title="No request outcomes yet"
        description="Request outcomes appear after the backend has observed traffic. Dashboard polling does not add requests here."
      />
    );
  }

  const data = [
    {
      name: "Allowed",
      value: metrics.allowedRequests,
      color: chartColors.accent,
    },
    {
      name: "Rate-limited (429)",
      value: metrics.rejectedRequests,
      color: chartColors.rose,
    },
  ];

  return (
    <div>
      <div className="h-[280px] w-full" data-testid="request-outcome-chart">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={data}
              dataKey="value"
              nameKey="name"
              cx="50%"
              cy="46%"
              innerRadius={62}
              outerRadius={92}
              paddingAngle={3}
              isAnimationActive={false}
            >
              {data.map((entry) => (
                <Cell key={entry.name} fill={entry.color} stroke="white" strokeWidth={2} />
              ))}
            </Pie>
            <Tooltip
              contentStyle={tooltipStyle}
              formatter={(value, name) => [
                `${formatInteger(Number(value))} (${formatPercent(
                  Number(value) / total,
                  1,
                )})`,
                name,
              ]}
            />
            <Legend
              wrapperStyle={legendStyle}
              iconType="circle"
              formatter={(value) => (
                <span className="text-xs text-slate-600">{value}</span>
              )}
            />
          </PieChart>
        </ResponsiveContainer>
      </div>
      <ChartDataTable
        caption="Cumulative limiter outcomes for this backend instance"
        getRowKey={(row) => row.name}
        rows={data}
        columns={[
          { key: "outcome", header: "Outcome", render: (row) => row.name },
          {
            key: "count",
            header: "Count",
            numeric: true,
            render: (row) => formatInteger(row.value),
          },
          {
            key: "share",
            header: "Share",
            numeric: true,
            render: (row) => formatPercent(row.value / total, 1),
          },
        ]}
      />
    </div>
  );
}
