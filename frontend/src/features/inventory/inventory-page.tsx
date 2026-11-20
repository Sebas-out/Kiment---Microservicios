import { requireSession } from "@/lib/auth/current-session";
import { SessionProblem } from "@/components/admin/session-problem";
import { Inventory } from "./inventory";

export async function InventoryPage({ transfer }: { transfer: boolean }) {
  const session = await requireSession();
  if (session.state !== "authenticated") return <SessionProblem embedded forbidden={session.state === "forbidden"} />;
  if (!session.profile.permissions.includes(transfer ? "STOCK_READ" : "MOVEMENT_READ")) return <h2>No tienes acceso a Inventario</h2>;
  return <Inventory transfer={transfer} account={session.profile.user.id} assignedBranchId={session.profile.user.branchId} canReadBranches={session.profile.user.role === "ADMIN" && session.profile.permissions.includes("BRANCH_READ")}
    user={session.profile.user.firstName + " " + session.profile.user.lastName}
    editable={session.profile.permissions.includes("STOCK_ADJUST") && (!transfer || session.profile.user.role === "ADMIN")} />;
}
