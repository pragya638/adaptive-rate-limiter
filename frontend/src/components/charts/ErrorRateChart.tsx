import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import type { TrafficHistoryPoint } from "../../lib/dashboardHistory";
import {
  formatDateTime,
  formatPercent,
  formatShortTime,
} from "../../lib/format";
import { ChartDataTable } from "./ChartDataTable";
import { ChartEmpty } from "./ChartEmpty";
import {
  axisProps,
  chartColors,
  legendStyle,
  tooltipStyle,
} from "./chartTheme";

export function ErrorRateChart({
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

  return (
    <div>
      <div className="h-[280px] w-full" data-testid="error-rate-chart">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
            <defs>
              <linearGradient id="clientErrorFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor={chartColors.amber} stopOpacity={0.25} />
                <stop offset="95%" stopColor={chartColors.amber} stopOpacity={0} />
              </linearGradient>
              <linearGradient id="cumulativeErrorFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor={chartColors.violet} stopOpacity={0.18} />
                <stop offset="95%" stopColor={chartColors.violet} stopOpacity={0} />
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
              tickFormatter={(value) => formatPercent(Number(value), 1)}
              width={54}
              {...axisProps}
            />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(value) => `Window ended ${formatDateTime(String(value))}`}
              formatter={(value, name) => [formatPercent(Number(value), 2), name]}
            />
            <Legend wrapperStyle={legendStyle} iconType="circle" />
            <Area
              type="monotone"
              dataKey="clientErrorRate"
              name="Client error rate (window)"
              stroke={chartColors.amber}
              strokeWidth={2}
              fill="url(#clientErrorFill)"
              dot={data.length === 1}
              activeDot={{ r: 4 }}
              isAnimationActive={false}
            />
            <Area
              type="monotone"
              dataKey="errorRate"
              name="Cumulative error rate at window end"
              stroke={chartColors.violet}
              strokeWidth={2}
              strokeDasharray="5 3"
              fill="url(#cumulativeErrorFill)"
              dot={data.length === 1}
              activeDot={{ r: 4 }}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <ChartDataTable
        caption="Error-rate trends by completed monitoring window"
        getRowKey={(point) => `${point.windowSequence}-${point.windowEnd}`}
        rows={data}
        columns={[
          {
            key: "windowEnd",
            header: "Window end",
            render: (point) => formatDateTime(point.windowEnd),
          },
          {
            key: "clientErrorRate",
            header: "Client error (window)",
            numeric: true,
            render: (point) => formatPercent(point.clientErrorRate, 2),
          },
          {
            key: "errorRate",
            header: "Cumulative error",
            numeric: true,
            render: (point) => formatPercent(point.errorRate, 2),
          },
        ]}
      />
    </div>
  );
}
