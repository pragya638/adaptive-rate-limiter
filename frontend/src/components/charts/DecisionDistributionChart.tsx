import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import {
  countDecisionActions,
  type DecisionDistribution,
} from "../../lib/decisionAnalytics";
import { formatInteger, formatPercent, titleCase } from "../../lib/format";
import { ChartDataTable } from "./ChartDataTable";
import { ChartEmpty } from "./ChartEmpty";
import { axisProps, chartColors, tooltipStyle } from "./chartTheme";

const actionColors: Record<keyof DecisionDistribution, string> = {
  INCREASE_LIMIT: chartColors.accent,
  DECREASE_LIMIT: chartColors.rose,
  KEEP_LIMIT: chartColors.slate,
  UNKNOWN: chartColors.amber,
  total: chartColors.slate,
};

export function DecisionDistributionChart({
  decisions,
  emptyTitle,
  emptyDescription,
}: {
  decisions: Parameters<typeof countDecisionActions>[0];
  emptyTitle?: string;
  emptyDescription?: string;
}) {
  const distribution = countDecisionActions(decisions);

  if (distribution.total === 0) {
    return <ChartEmpty title={emptyTitle} description={emptyDescription} />;
  }

  const data = (
    ["INCREASE_LIMIT", "DECREASE_LIMIT", "KEEP_LIMIT", "UNKNOWN"] as const
  )
    .filter((key) => distribution[key] > 0)
    .map((key) => ({
      key,
      label: titleCase(key),
      count: distribution[key],
      share: distribution[key] / distribution.total,
    }));

  return (
    <div>
      <div className="h-[240px] w-full" data-testid="decision-distribution-chart">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={data}
            layout="vertical"
            margin={{ top: 4, right: 12, left: 8, bottom: 4 }}
          >
            <CartesianGrid
              strokeDasharray="3 3"
              stroke={chartColors.grid}
              horizontal={false}
            />
            <XAxis
              type="number"
              allowDecimals={false}
              tickFormatter={(value) => formatInteger(Number(value))}
              {...axisProps}
            />
            <YAxis
              type="category"
              dataKey="label"
              width={108}
              {...axisProps}
            />
            <Tooltip
              contentStyle={tooltipStyle}
              formatter={(value, _name, item) => [
                `${formatInteger(Number(value))} (${formatPercent(
                  Number((item.payload as { share: number }).share),
                  1,
                )})`,
                "Decisions",
              ]}
            />
            <Bar dataKey="count" name="Decisions" radius={[0, 4, 4, 0]} isAnimationActive={false}>
              {data.map((entry) => (
                <Cell key={entry.key} fill={actionColors[entry.key]} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <ChartDataTable
        caption="Distribution of adaptive decision actions"
        getRowKey={(row) => row.key}
        rows={data}
        columns={[
          { key: "action", header: "Action", render: (row) => row.label },
          {
            key: "count",
            header: "Count",
            numeric: true,
            render: (row) => formatInteger(row.count),
          },
          {
            key: "share",
            header: "Share",
            numeric: true,
            render: (row) => formatPercent(row.share, 1),
          },
        ]}
      />
    </div>
  );
}
