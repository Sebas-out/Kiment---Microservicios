import type { LucideIcon } from "lucide-react";
export function EmptyState({ icon: Icon, title, children }: {
  icon: LucideIcon; title: string; children: React.ReactNode;
}) {
  return <div className="admin-empty-state">
    <span className="admin-empty-icon" aria-hidden="true"><Icon size={30} strokeWidth={1.3} /></span>
    <h3>{title}</h3><p>{children}</p>
  </div>;
}
