import { Download } from "lucide-react";

import { downloadCsv } from "../../lib/csv";

interface CsvExportButtonProps {
  filename: string;
  csv: string;
  label?: string;
  disabled?: boolean;
}

export function CsvExportButton({
  filename,
  csv,
  label = "Export CSV",
  disabled = false,
}: CsvExportButtonProps) {
  return (
    <button
      type="button"
      className="btn-secondary whitespace-nowrap px-3 py-1.5 text-xs"
      onClick={() => downloadCsv(filename, csv)}
      disabled={disabled}
      aria-label={`${label}. Downloads filtered backend history.`}
    >
      <Download aria-hidden="true" className="h-3.5 w-3.5" />
      {label}
    </button>
  );
}
