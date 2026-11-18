import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import type { LimitHistoryPoint } from "../../lib/dashboardHistory";
import { formatDateTime, formatInteger, formatShortTime, titleCase } from "../../lib/format";
import { ChartDataTable } from "./ChartDataTable";
import { ChartEmpty } from "./ChartEmpty";
import {
  axisProps,
  chartColors,
  legendStyle,
  tooltipStyle,
} from "./chartTheme";

export function LimitTimelineChart({
  data,
  emptyTitle,
  emptyDescription,
}: {
  data: LimitHistoryPoint[];
  emptyTitle?: string;
  emptyDescription?: string;
}) {
  if (data.length === 0) {
    return (
      <ChartEmpty
        title={emptyTitle ?? "No limit observations yet"}
        description={
          emptyDescription ??
          "The limit timeline appears after the backend completes adaptive evaluations."
        }
      />
    );
  }

  return (
    <div>
      <div className="h-[280px] w-full" data-testid="limit-timeline-chart">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 8, left: -8, bottom: 0 }}>
            <CartesianGrid
              strokeDasharray="3 3"
              stroke={chartColors.grid}
              vertical={false}
            />
            <XAxis
              dataKey="timestamp"
              tickFormatter={(value) => formatShortTime(String(value))}
              minTickGap={28}
              {...axisProps}
            />
            <YAxis
              tickFormatter={(value) => formatInteger(Number(value))}
              width={48}
              allowDecimals={false}
              {...axisProps}
            />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(value) => `Observed around ${formatDateTime(String(value))}`}
              formatter={(value) => [`${formatInteger(Number(value))} requests / window`]}
            />
            <Legend wrapperStyle={legendStyle} iconType="circle" />
            <Line
              type="stepAfter"
              dataKey="limit"
              name="Active limit"
              stroke={chartColors.violet}
              strokeWidth={2.5}
              dot={data.length === 1}
              activeDot={{ r: 4 }}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
      <ChartDataTable
        caption="Adaptive limit observations by evaluation time"
        getRowKey={(point) => point.key}
        rows={data}
        columns={[
          {
            key: "timestamp",
            header: "Evaluated",
            render: (point) => formatDateTime(point.timestamp),
          },
          {
            key: "previous",
            header: "Previous",
            numeric: true,
            render: (point) => formatInteger(point.previousLimit),
          },
          {
            key: "applied",
            header: "Applied",
            numeric: true,
            render: (point) => formatInteger(point.limit),
          },
          {
            key: "action",
            header: "Action",
            render: (point) => titleCase(point.action),
          },
        ]}
      />
    </div>
  );
}
