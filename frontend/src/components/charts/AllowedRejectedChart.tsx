import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import type { TrafficHistoryPoint } from "../../lib/dashboardHistory";
import { formatDateTime, formatInteger, formatPercent, formatShortTime } from "../../lib/format";
import { ChartDataTable } from "./ChartDataTable";
import { ChartEmpty } from "./ChartEmpty";
import {
  axisProps,
  chartColors,
  legendStyle,
  tooltipStyle,
} from "./chartTheme";

export function outcomeTotals(point: TrafficHistoryPoint) {
  return point.allowedRequests + point.rejectedRequests;
}

export function rejectionRate(point: TrafficHistoryPoint): number {
  const total = outcomeTotals(point);
  return total === 0 ? 0 : point.rejectedRequests / total;
}

export function AllowedRejectedChart({
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
      <div className="h-[280px] w-full" data-testid="allowed-rejected-chart">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, left: -12, bottom: 0 }}>
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
              tickFormatter={(value) => formatInteger(Number(value))}
              width={52}
              allowDecimals={false}
              {...axisProps}
            />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(value) => `Window ended ${formatDateTime(String(value))}`}
              formatter={(value, name) => [
                formatInteger(Number(value)),
                name,
              ]}
            />
            <Legend wrapperStyle={legendStyle} iconType="circle" />
            <Bar
              dataKey="allowedRequests"
              name="Allowed by limiter"
              fill={chartColors.accent}
              radius={[3, 3, 0, 0]}
              isAnimationActive={false}
            />
            <Bar
              dataKey="rejectedRequests"
              name="Rejected with 429"
              fill={chartColors.rose}
              radius={[3, 3, 0, 0]}
              isAnimationActive={false}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
      <ChartDataTable
        caption="Allowed and rejected requests by completed monitoring window"
        getRowKey={(point) => `${point.windowSequence}-${point.windowEnd}`}
        rows={data}
        columns={[
          {
            key: "windowEnd",
            header: "Window end",
            render: (point) => formatDateTime(point.windowEnd),
          },
          {
            key: "allowed",
            header: "Allowed",
            numeric: true,
            render: (point) => formatInteger(point.allowedRequests),
          },
          {
            key: "rejected",
            header: "Rejected",
            numeric: true,
            render: (point) => formatInteger(point.rejectedRequests),
          },
          {
            key: "rejectionRate",
            header: "Rejection rate",
            numeric: true,
            render: (point) => formatPercent(rejectionRate(point), 1),
          },
        ]}
      />
    </div>
  );
}
