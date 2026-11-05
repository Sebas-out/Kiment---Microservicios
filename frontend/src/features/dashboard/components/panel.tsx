import { Inbox } from "lucide-react";
export function Panel({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return <section className="admin-card dash-panel" aria-label={title}><header><h3>{title}</h3><p>{description}</p></header>{children}</section>;
}
export function NoData({ children }: { children: React.ReactNode }) {
  return <div className="dash-no-data"><Inbox size={25} strokeWidth={1.3} aria-hidden="true" /><p>{children}</p></div>;
}
