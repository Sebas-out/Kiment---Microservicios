import { requireSession } from "@/lib/auth/current-session";
import { SessionProblem } from "@/components/admin/session-problem";
import { Inventory } from "./inventory";

export async function InventoryPage({ transfer }: { transfer: boolean }) {
  const session = await requireSession();
  if (session.state !== "authenticated") return <SessionProblem embedded forbidden={session.state === "forbidden"} />;
  if (!session.profile.permissions.some(permission => ["STOCK_READ", "MOVEMENT_READ"].includes(permission))) return <h2>No tienes acceso a Inventario</h2>;
  return <Inventory transfer={transfer} account={session.profile.user.id}
    user={session.profile.user.firstName + " " + session.profile.user.lastName}
    editable={session.profile.user.role === "ADMIN"} />;
}
