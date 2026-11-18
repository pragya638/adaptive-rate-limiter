import { Navigate, Route, Routes } from "react-router-dom";

import { AppShell } from "./components/layout/AppShell";
import { AnomaliesPage } from "./pages/AnomaliesPage";
import { DecisionsPage } from "./pages/DecisionsPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { OverviewPage } from "./pages/OverviewPage";
import { RateLimitingPage } from "./pages/RateLimitingPage";
import { SystemStatusPage } from "./pages/SystemStatusPage";
import { TrafficPage } from "./pages/TrafficPage";

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<Navigate to="/overview" replace />} />
        <Route path="/overview" element={<OverviewPage />} />
        <Route path="/rate-limiting" element={<RateLimitingPage />} />
        <Route path="/traffic" element={<TrafficPage />} />
        <Route path="/anomalies" element={<AnomaliesPage />} />
        <Route path="/decisions" element={<DecisionsPage />} />
        <Route path="/system-status" element={<SystemStatusPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
