import { requireSession } from "@/lib/auth/current-session";
import { SessionProblem } from "@/components/admin/session-problem";
import type { Metadata } from "next";
import { Dashboard } from "@/features/dashboard/components/dashboard";
import { limaDate } from "@/features/dashboard/filters";
export const metadata: Metadata = { title: "Dashboard | KIMENTS POS" };
export default async function DashboardPage() {
  const session = await requireSession();
  if (session.state !== "authenticated") return <SessionProblem embedded forbidden={session.state === "forbidden"} />;
  return <Dashboard today={limaDate()} admin={session.profile.user.role === "ADMIN"} />;
}
