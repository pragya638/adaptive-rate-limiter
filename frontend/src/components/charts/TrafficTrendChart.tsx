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
  formatDecimal,
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

export function TrafficTrendChart({
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
      <div className="h-[280px] w-full" data-testid="traffic-trend-chart">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
            <defs>
              <linearGradient id="currentRateFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor={chartColors.brand} stopOpacity={0.2} />
                <stop offset="95%" stopColor={chartColors.brand} stopOpacity={0} />
              </linearGradient>
              <linearGradient id="ewmaFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor={chartColors.accent} stopOpacity={0.15} />
                <stop offset="95%" stopColor={chartColors.accent} stopOpacity={0} />
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
              tickFormatter={(value) => formatDecimal(Number(value))}
              width={52}
              {...axisProps}
            />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(value) => `Window ended ${formatDateTime(String(value))}`}
              formatter={(value) => [`${formatDecimal(Number(value))} req/s`]}
            />
            <Legend wrapperStyle={legendStyle} iconType="circle" />
            <Area
              type="monotone"
              dataKey="requestRate"
              name="Current rate"
              stroke={chartColors.brand}
              strokeWidth={2}
              fill="url(#currentRateFill)"
              dot={data.length === 1}
              activeDot={{ r: 4 }}
              isAnimationActive={false}
            />
            <Area
              type="monotone"
              dataKey="ewmaRequestRate"
              name="EWMA rate"
              stroke={chartColors.accent}
              strokeWidth={2}
              fill="url(#ewmaFill)"
              dot={data.length === 1}
              activeDot={{ r: 4 }}
              isAnimationActive={false}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <ChartDataTable
        caption="Request rate and EWMA by completed monitoring window"
        getRowKey={(point) => `${point.windowSequence}-${point.windowEnd}`}
        rows={data}
        columns={[
          {
            key: "windowEnd",
            header: "Window end",
            render: (point) => formatDateTime(point.windowEnd),
          },
          {
            key: "requestRate",
            header: "Rate",
            numeric: true,
            render: (point) => `${formatDecimal(point.requestRate)} req/s`,
          },
          {
            key: "ewma",
            header: "EWMA",
            numeric: true,
            render: (point) => `${formatDecimal(point.ewmaRequestRate)} req/s`,
          },
        ]}
      />
    </div>
  );
}
