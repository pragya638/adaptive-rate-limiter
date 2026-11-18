import { Compass } from "lucide-react";
import { Link } from "react-router-dom";

import { EmptyState } from "../components/ui/EmptyState";
import { PageHeader } from "../components/ui/PageHeader";

export function NotFoundPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Navigation"
        title="Page not found"
        description="The requested dashboard view does not exist."
      />
      <EmptyState
        icon={<Compass aria-hidden="true" className="h-5 w-5" />}
        title="Nothing at this route"
        description="Use the sidebar to return to an operational view."
        action={
          <Link to="/overview" className="btn-secondary">
            Return to overview
          </Link>
        }
      />
    </div>
  );
}
