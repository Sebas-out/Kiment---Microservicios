import { requireSession } from "@/lib/auth/current-session";
import { SessionProblem } from "@/components/admin/session-problem";
import { CatalogManager } from "./catalog-manager";
import type { Kind } from "./catalog-store";
import "./products.css";

export async function CatalogPage({ kind }: { kind: Kind }) {
  const session = await requireSession();
  if (session.state !== "authenticated") return <SessionProblem embedded forbidden={session.state === "forbidden"} />;
  const permission = kind === "categories" ? "CATEGORY_WRITE" : "VARIANT_WRITE";
  const canEdit = session.profile.permissions.includes(permission);
  if (!canEdit && !session.profile.permissions.includes("PRODUCT_READ")) return <h2>No tienes acceso a este apartado</h2>;
  return <CatalogManager key={session.profile.user.id + kind} kind={kind} canEdit={canEdit} account={session.profile.user.id} />;
}