import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import type { AnomalyHistoryPoint } from "../../lib/dashboardHistory";
import {
  formatDateTime,
  formatDecimal,
  formatShortTime,
  titleCase,
} from "../../lib/format";
import { ChartDataTable } from "./ChartDataTable";
import { ChartEmpty } from "./ChartEmpty";
import {
  axisProps,
  chartColors,
  legendStyle,
  tooltipStyle,
} from "./chartTheme";

export function AnomalyHistoryChart({
  data,
  emptyTitle,
  emptyDescription,
}: {
  data: AnomalyHistoryPoint[];
  emptyTitle?: string;
  emptyDescription?: string;
}) {
  const available = data.filter(
    (point) => point.available && point.anomalyScore !== null,
  );

  if (available.length === 0) {
    return (
      <ChartEmpty
        title={
          emptyTitle ??
          (data.length > 0
            ? "No scored assessments in this range"
            : "No scored assessments yet")
        }
        description={
          emptyDescription ??
          "Anomaly scores appear after adaptive evaluations return an available ML assessment."
        }
      />
    );
  }

  return (
    <div>
      <div className="h-[280px] w-full" data-testid="anomaly-history-chart">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={available}
            margin={{ top: 8, right: 8, left: -12, bottom: 0 }}
          >
            <CartesianGrid
              strokeDasharray="3 3"
              stroke={chartColors.grid}
              vertical={false}
            />
            <XAxis
              dataKey="timestamp"
              tickFormatter={(value) => formatShortTime(String(value))}
              minTickGap={24}
              {...axisProps}
            />
            <YAxis
              tickFormatter={(value) => formatDecimal(Number(value))}
              width={52}
              {...axisProps}
            />
            <Tooltip
              contentStyle={tooltipStyle}
              labelFormatter={(value) => `Evaluated ${formatDateTime(String(value))}`}
              formatter={(value) => [formatDecimal(Number(value)), "Anomaly score"]}
            />
            <Legend wrapperStyle={legendStyle} iconType="circle" />
            <Bar
              dataKey="anomalyScore"
              name="Anomaly score"
              radius={[4, 4, 0, 0]}
              isAnimationActive={false}
            >
              {available.map((point) => (
                <Cell
                  key={point.key}
                  fill={point.isAnomaly ? chartColors.rose : chartColors.brand}
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <ChartDataTable
        caption="Anomaly assessments by evaluation time"
        getRowKey={(point) => point.key}
        rows={available}
        columns={[
          {
            key: "timestamp",
            header: "Evaluated",
            render: (point) => formatDateTime(point.timestamp),
          },
          {
            key: "score",
            header: "Score",
            numeric: true,
            render: (point) => formatDecimal(point.anomalyScore),
          },
          {
            key: "prediction",
            header: "Prediction",
            render: (point) => titleCase(point.prediction),
          },
          {
            key: "result",
            header: "Result",
            render: (point) =>
              point.isAnomaly ? "Anomaly" : point.isAnomaly === false ? "Normal" : "—",
          },
        ]}
      />
    </div>
  );
}
