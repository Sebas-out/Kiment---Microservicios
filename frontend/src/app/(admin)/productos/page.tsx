import { requireSession } from "@/lib/auth/current-session";
import { SessionProblem } from "@/components/admin/session-problem";
import { Products } from "@/features/products/products";
import "@/features/products/products.css";

export const metadata = { title: "Productos | KIMENTS POS" };

export default async function ProductsPage() {
  const session = await requireSession();
  if (session.state !== "authenticated") return <SessionProblem embedded forbidden={session.state === "forbidden"} />;
  if (!session.profile.permissions.includes("PRODUCT_READ")) return <h2>No tienes acceso a Productos</h2>;
  return <Products key={session.profile.user.id} canReport={session.profile.permissions.includes("STOCK_REPORT_READ")} accountId={session.profile.user.id} assignedBranchId={session.profile.user.branchId} editable={session.profile.permissions.includes("PRODUCT_WRITE")} canReadBranches={session.profile.user.role === "ADMIN" && session.profile.permissions.includes("BRANCH_READ")} />;
}
