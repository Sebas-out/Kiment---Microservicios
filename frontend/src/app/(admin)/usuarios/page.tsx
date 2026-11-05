import type { Metadata } from "next";
import Link from "next/link";
import { ShieldX } from "lucide-react";
import { requireSession } from "@/lib/auth/current-session";
import { canReadUsers } from "@/features/auth/contracts";
import { SessionProblem } from "@/components/admin/session-problem";
import { EmptyState } from "@/components/admin/empty-state";
import { Users } from "@/features/users/components/users";
export const metadata: Metadata = { title: "Usuarios | KIMENTS POS" };
export default async function UsersPage() {
  const session = await requireSession();
  if (session.state !== "authenticated") return <SessionProblem embedded forbidden={session.state === "forbidden"} />;
  if (!canReadUsers(session.profile)) {
    return <section className="admin-card admin-access-denied"><EmptyState icon={ShieldX} title="No tienes acceso a Usuarios">Esta sección requiere permisos de administración.</EmptyState><Link className="admin-text-link" href="/dashboard">Volver al dashboard</Link></section>;
  }
  return <Users actorId={session.profile.user.id} permissions={session.profile.permissions} />;
}
