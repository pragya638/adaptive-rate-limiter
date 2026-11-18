import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import type { TrafficHistoryPoint } from "../../lib/dashboardHistory";
import { formatDateTime, formatPercent, formatShortTime } from "../../lib/format";
import { ChartDataTable } from "./ChartDataTable";
import { ChartEmpty } from "./ChartEmpty";
import { axisProps, chartColors, tooltipStyle } from "./chartTheme";
import { outcomeTotals, rejectionRate } from "./AllowedRejectedChart";

export function RejectionRateChart({
  data,
  emptyTitle,
  emptyDescription,
}: {
  data: TrafficHistoryPoint[];
  emptyTitle?: string;
  emptyDescription?: string;
}) {
  if (data.length === 0) {
    return <ChartEmpty title={emptyTitle} description={emptyDescription} />;
  }

  const chartData = data.map((point) => ({
    ...point,
    rejectionRate: rejectionRate(point),
  }));

  return (
    <div>
      <div className="h-[280px] w-full" data-testid="rejection-rate-chart">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={chartData} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
            <defs>
              <linearGradient id="rejectionRateFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor={chartColors.rose} stopOpacity={0.28} />
                <stop offset="95%" stopColor={chartColors.rose} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid
              strokeDasharray="3 3"
              stroke={chartColors.grid}
              vertical={false}
            />
            <XAxis
              dataKey="windowEnd"
              tickFormatter={(value) => formatShortTime(String(value))}
              minTickGap={28}
              {...axisProps}
            />
            <YAxis
              tickFormatter={(value) => formatPercent(Number(value), 0)}
              width={54}
              {...axisProps}
            />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(value) => `Window ended ${formatDateTime(String(value))}`}
              formatter={(value) => [formatPercent(Number(value), 2), "Rejection rate"]}
            />
            <Area
              type="monotone"
              dataKey="rejectionRate"
              name="Rejection rate"
              stroke={chartColors.rose}
              strokeWidth={2}
              fill="url(#rejectionRateFill)"
              dot={data.length === 1}
              activeDot={{ r: 4 }}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <ChartDataTable
        caption="Rejection rate by completed monitoring window"
        getRowKey={(point) => `${point.windowSequence}-${point.windowEnd}`}
        rows={data}
        columns={[
          {
            key: "windowEnd",
            header: "Window end",
            render: (point) => formatDateTime(point.windowEnd),
          },
          {
            key: "outcomes",
            header: "Outcomes",
            numeric: true,
            render: (point) => String(outcomeTotals(point)),
          },
          {
            key: "rate",
            header: "Rejection rate",
            numeric: true,
            render: (point) => formatPercent(rejectionRate(point), 2),
          },
        ]}
      />
    </div>
  );
}
