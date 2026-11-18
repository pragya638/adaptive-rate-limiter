import type { ReactNode } from "react";
import { Table2 } from "lucide-react";

export interface ChartTableColumn<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  numeric?: boolean;
}

interface ChartDataTableProps<T> {
  caption: string;
  columns: ChartTableColumn<T>[];
  rows: T[];
  getRowKey: (row: T) => string | number;
  summary?: string;
}

export function ChartDataTable<T>({
  caption,
  columns,
  rows,
  getRowKey,
  summary = "View chart data as a table",
}: ChartDataTableProps<T>) {
  if (rows.length === 0) {
    return null;
  }

  return (
    <details className="mt-3 rounded-lg border border-slate-200 bg-slate-50/60">
      <summary className="flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 focus-ring">
        <Table2 aria-hidden="true" className="h-3.5 w-3.5" />
        {summary}
      </summary>
      <div className="table-shell max-h-72 overflow-y-auto border-t border-slate-200">
        <table className="data-table">
          <caption className="sr-only">{caption}</caption>
          <thead>
            <tr>
              {columns.map((column) => (
                <th
                  key={column.key}
                  scope="col"
                  className={column.numeric ? "text-right" : undefined}
                >
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={getRowKey(row)}>
                {columns.map((column) => (
                  <td
                    key={column.key}
                    className={
                      column.numeric
                        ? "text-right font-mono text-slate-700"
                        : undefined
                    }
                  >
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  );
}
