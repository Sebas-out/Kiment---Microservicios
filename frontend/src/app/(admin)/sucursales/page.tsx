import type { Metadata } from "next";
import { requireSession } from "@/lib/auth/current-session";
import { SessionProblem } from "@/components/admin/session-problem";
import { Branches } from "@/features/branches/components/branches";
export const metadata: Metadata = { title: "Sucursales | KIMENTS POS" };
export default async function BranchesPage() {
  const session = await requireSession();
  if (session.state !== "authenticated") return <SessionProblem embedded forbidden={session.state === "forbidden"} />;
  if (session.profile.user.role !== "ADMIN" || !session.profile.permissions.includes("BRANCH_READ")) return <section className="admin-card admin-access-denied"><h2>No tienes acceso a Sucursales</h2><p>Esta sección requiere permiso de administración de sucursales.</p></section>;
  return <Branches permissions={session.profile.permissions} />;
}
