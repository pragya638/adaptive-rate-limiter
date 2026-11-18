"""Dependency-free SVG chart generation."""

from __future__ import annotations

import math
from pathlib import Path
from xml.sax.saxutils import escape

COLORS = ["#2563eb", "#dc2626", "#059669", "#d97706", "#7c3aed", "#0891b2"]


def _finite(values):
    return [
        float(value)
        for value in values
        if value is not None and math.isfinite(float(value))
    ]


def _format(value: float, pattern: str) -> str:
    return pattern.format(value)


def bar_chart_svg(
    path: Path,
    title: str,
    categories: list[str],
    series: dict[str, list[float | None]],
    y_label: str,
    value_format: str = "{:.2f}",
    width: int = 980,
    height: int = 480,
    errors: dict[str, list[float | None]] | None = None,
) -> None:
    left, right, top, bottom = 90, 30, 70, 110
    plot_width = width - left - right
    plot_height = height - top - bottom
    all_values = _finite(value for values in series.values() for value in values)
    if errors:
        for label, error_values in errors.items():
            values = series.get(label, [])
            for index, error in enumerate(error_values):
                if error is None or index >= len(values) or values[index] is None:
                    continue
                if not math.isfinite(float(error)):
                    continue
                all_values.append(float(values[index]) + abs(float(error)))
    maximum = max(all_values) if all_values else 1.0
    maximum = maximum if maximum > 0 else 1.0
    maximum *= 1.1
    group_width = plot_width / max(1, len(categories))
    bar_width = min(44.0, group_width / max(1, len(series)) * 0.72)
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{left}" y="36" font-family="Arial, sans-serif" font-size="22" font-weight="bold" fill="#111827">{escape(title)}</text>',
    ]
    tick_count = 5
    for index in range(tick_count + 1):
        value = maximum * index / tick_count
        y = top + plot_height - (index / tick_count) * plot_height
        parts.append(
            f'<line x1="{left}" y1="{y:.2f}" x2="{left + plot_width}" y2="{y:.2f}" stroke="#e5e7eb" stroke-width="1"/>'
        )
        parts.append(
            f'<text x="{left - 10}" y="{y + 5:.2f}" text-anchor="end" font-family="Arial, sans-serif" font-size="12" fill="#4b5563">{escape(_format(value, value_format))}</text>'
        )
    parts.append(
        f'<line x1="{left}" y1="{top + plot_height}" x2="{left + plot_width}" y2="{top + plot_height}" stroke="#374151" stroke-width="1.5"/>'
    )
    parts.append(
        f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top + plot_height}" stroke="#374151" stroke-width="1.5"/>'
    )
    parts.append(
        f'<text x="22" y="{top + plot_height / 2}" transform="rotate(-90 22 {top + plot_height / 2})" text-anchor="middle" font-family="Arial, sans-serif" font-size="13" fill="#374151">{escape(y_label)}</text>'
    )
    for category_index, category in enumerate(categories):
        center = left + (category_index + 0.5) * group_width
        parts.append(
            f'<text x="{center:.2f}" y="{top + plot_height + 26}" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" fill="#111827">{escape(category)}</text>'
        )
        offset = -(len(series) - 1) * bar_width / 2
        for series_index, (label, values) in enumerate(series.items()):
            value = values[category_index] if category_index < len(values) else None
            if value is None or not math.isfinite(float(value)):
                continue
            bar_height = (float(value) / maximum) * plot_height if maximum > 0 else 0.0
            x = center + offset + series_index * bar_width
            y = top + plot_height - bar_height
            color = COLORS[series_index % len(COLORS)]
            parts.append(
                f'<rect x="{x:.2f}" y="{y:.2f}" width="{bar_width * 0.82:.2f}" height="{bar_height:.2f}" fill="{color}"/>'
            )
            parts.append(
                f'<text x="{x + bar_width * 0.41:.2f}" y="{max(top + 14, y - 6):.2f}" text-anchor="middle" font-family="Arial, sans-serif" font-size="10" fill="#374151">{escape(_format(float(value), value_format))}</text>'
            )
            error_values = (errors or {}).get(label)
            if error_values is not None and category_index < len(error_values):
                error = error_values[category_index]
                if error is not None and math.isfinite(float(error)):
                    margin = abs(float(error))
                    center_x = x + bar_width * 0.41
                    low = max(0.0, float(value) - margin)
                    high = float(value) + margin
                    low_y = top + plot_height - (low / maximum) * plot_height
                    high_y = top + plot_height - (high / maximum) * plot_height
                    parts.append(
                        f'<line x1="{center_x:.2f}" y1="{high_y:.2f}" x2="{center_x:.2f}" y2="{low_y:.2f}" stroke="#111827" stroke-width="1.5"/>'
                    )
                    parts.append(
                        f'<line x1="{center_x - 5:.2f}" y1="{high_y:.2f}" x2="{center_x + 5:.2f}" y2="{high_y:.2f}" stroke="#111827" stroke-width="1.5"/>'
                    )
                    parts.append(
                        f'<line x1="{center_x - 5:.2f}" y1="{low_y:.2f}" x2="{center_x + 5:.2f}" y2="{low_y:.2f}" stroke="#111827" stroke-width="1.5"/>'
                    )
    legend_y = height - 42
    legend_x = left
    for series_index, label in enumerate(series):
        color = COLORS[series_index % len(COLORS)]
        parts.append(
            f'<rect x="{legend_x}" y="{legend_y - 12}" width="14" height="14" fill="{color}"/>'
        )
        parts.append(
            f'<text x="{legend_x + 20}" y="{legend_y}" font-family="Arial, sans-serif" font-size="13" fill="#111827">{escape(label)}</text>'
        )
        legend_x += 150
    if not all_values:
        parts.append(
            f'<text x="{left + plot_width / 2}" y="{top + plot_height / 2}" text-anchor="middle" font-family="Arial, sans-serif" font-size="16" fill="#6b7280">No data</text>'
        )
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")


def line_chart_svg(
    path: Path,
    title: str,
    series: dict[str, list[tuple[float, float]]],
    x_label: str,
    y_label: str,
    value_format: str = "{:.0f}",
    width: int = 980,
    height: int = 440,
) -> None:
    left, right, top, bottom = 90, 30, 70, 90
    plot_width = width - left - right
    plot_height = height - top - bottom
    all_points = [point for points in series.values() for point in points]
    x_max = max((point[0] for point in all_points), default=1.0)
    y_max = max((point[1] for point in all_points), default=1.0)
    x_max = x_max if x_max > 0 else 1.0
    y_max = y_max if y_max > 0 else 1.0
    y_max *= 1.1
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text x="{left}" y="36" font-family="Arial, sans-serif" font-size="22" font-weight="bold" fill="#111827">{escape(title)}</text>',
    ]
    tick_count = 5
    for index in range(tick_count + 1):
        value = y_max * index / tick_count
        y = top + plot_height - (index / tick_count) * plot_height
        parts.append(
            f'<line x1="{left}" y1="{y:.2f}" x2="{left + plot_width}" y2="{y:.2f}" stroke="#e5e7eb" stroke-width="1"/>'
        )
        parts.append(
            f'<text x="{left - 10}" y="{y + 5:.2f}" text-anchor="end" font-family="Arial, sans-serif" font-size="12" fill="#4b5563">{escape(_format(value, value_format))}</text>'
        )
    for index in range(tick_count + 1):
        value = x_max * index / tick_count
        x = left + (index / tick_count) * plot_width
        parts.append(
            f'<line x1="{x:.2f}" y1="{top + plot_height}" x2="{x:.2f}" y2="{top + plot_height + 6}" stroke="#374151" stroke-width="1"/>'
        )
        parts.append(
            f'<text x="{x:.2f}" y="{top + plot_height + 24}" text-anchor="middle" font-family="Arial, sans-serif" font-size="12" fill="#4b5563">{escape(_format(value, "{:.1f}"))}</text>'
        )
    parts.append(
        f'<line x1="{left}" y1="{top + plot_height}" x2="{left + plot_width}" y2="{top + plot_height}" stroke="#374151" stroke-width="1.5"/>'
    )
    parts.append(
        f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top + plot_height}" stroke="#374151" stroke-width="1.5"/>'
    )
    parts.append(
        f'<text x="22" y="{top + plot_height / 2}" transform="rotate(-90 22 {top + plot_height / 2})" text-anchor="middle" font-family="Arial, sans-serif" font-size="13" fill="#374151">{escape(y_label)}</text>'
    )
    parts.append(
        f'<text x="{left + plot_width / 2}" y="{height - 20}" text-anchor="middle" font-family="Arial, sans-serif" font-size="13" fill="#374151">{escape(x_label)}</text>'
    )
    for series_index, (label, points) in enumerate(series.items()):
        ordered = sorted(points, key=lambda point: point[0])
        if not ordered:
            continue
        color = COLORS[series_index % len(COLORS)]
        coordinates = " ".join(
            f"{left + (point[0] / x_max) * plot_width:.2f},{top + plot_height - (point[1] / y_max) * plot_height:.2f}"
            for point in ordered
        )
        parts.append(
            f'<polyline points="{coordinates}" fill="none" stroke="{color}" stroke-width="2.5"/>'
        )
    legend_y = height - 42
    legend_x = left
    for series_index, label in enumerate(series):
        color = COLORS[series_index % len(COLORS)]
        parts.append(
            f'<rect x="{legend_x}" y="{legend_y - 12}" width="14" height="14" fill="{color}"/>'
        )
        parts.append(
            f'<text x="{legend_x + 20}" y="{legend_y}" font-family="Arial, sans-serif" font-size="13" fill="#111827">{escape(label)}</text>'
        )
        legend_x += 170
    if not all_points:
        parts.append(
            f'<text x="{left + plot_width / 2}" y="{top + plot_height / 2}" text-anchor="middle" font-family="Arial, sans-serif" font-size="16" fill="#6b7280">No data</text>'
        )
    parts.append("</svg>")
    path.write_text("\n".join(parts), encoding="utf-8")
