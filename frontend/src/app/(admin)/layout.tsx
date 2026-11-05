import { requireSession } from "@/lib/auth/current-session";
import { allowedNavigationIds } from "@/features/navigation/config";
import { AdminShell } from "@/components/admin/admin-shell";
import { SessionProblem } from "@/components/admin/session-problem";
import { SessionGuard } from "@/features/auth/components/session-guard";
import { AccountAvatarProvider } from "@/features/account/avatar-state";
import "./admin.css";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  const session = await requireSession();
  if (session.state !== "authenticated") return <SessionProblem forbidden={session.state === "forbidden"} />;
  const { id, firstName, lastName, email, role, hasProfileImage, updatedAt } = session.profile.user;
  return <AccountAvatarProvider key={id} present={hasProfileImage === true} revision={updatedAt ?? "current"}>
    <SessionGuard userId={id} />
    <AdminShell identity={{ firstName, lastName, email, role }} allowedIds={allowedNavigationIds(session.profile)}>{children}</AdminShell>
  </AccountAvatarProvider>;
}
