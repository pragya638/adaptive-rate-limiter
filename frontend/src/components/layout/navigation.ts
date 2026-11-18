import {
  Activity,
  Gauge,
  LayoutDashboard,
  ServerCog,
  ShieldAlert,
  Sparkles,
  type LucideIcon,
} from "lucide-react";

export interface NavItem {
  label: string;
  path: string;
  icon: LucideIcon;
  description: string;
}

export interface NavGroup {
  label: string;
  items: NavItem[];
}

export const navigationGroups: NavGroup[] = [
  {
    label: "Operations",
    items: [
      {
        label: "Overview",
        path: "/overview",
        icon: LayoutDashboard,
        description: "Operational snapshot",
      },
      {
        label: "Rate Limiting",
        path: "/rate-limiting",
        icon: Gauge,
        description: "Static and adaptive views",
      },
      {
        label: "Traffic Monitoring",
        path: "/traffic",
        icon: Activity,
        description: "Windows, rates and outcomes",
      },
    ],
  },
  {
    label: "Intelligence",
    items: [
      {
        label: "Anomaly Detection",
        path: "/anomalies",
        icon: ShieldAlert,
        description: "Isolation Forest assessments",
      },
      {
        label: "Adaptive Decisions",
        path: "/decisions",
        icon: Sparkles,
        description: "Controller output timeline",
      },
    ],
  },
  {
    label: "Platform",
    items: [
      {
        label: "System Status",
        path: "/system-status",
        icon: ServerCog,
        description: "Dependencies and visibility",
      },
    ],
  },
];

export const pageMeta: Record<string, { title: string; section: string }> = {
  "/overview": { title: "Overview", section: "Operations" },
  "/rate-limiting": { title: "Rate Limiting", section: "Operations" },
  "/traffic": { title: "Traffic Monitoring", section: "Operations" },
  "/anomalies": { title: "Anomaly Detection", section: "Intelligence" },
  "/decisions": { title: "Adaptive Decisions", section: "Intelligence" },
  "/system-status": { title: "System Status", section: "Platform" },
};
