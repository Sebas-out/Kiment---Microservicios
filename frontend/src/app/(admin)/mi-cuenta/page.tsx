import type { Metadata } from "next";
import { requireSession } from "@/lib/auth/current-session";
import { SessionProblem } from "@/components/admin/session-problem";
import { Account } from "@/features/account/account";
import "@/features/account/account.css";

export const metadata: Metadata = { title: "Configuración de Cuenta | KIMENTS POS" };

export default async function AccountPage() {
  const session = await requireSession();
  if (session.state !== "authenticated") return <SessionProblem forbidden={session.state === "forbidden"} />;
  return <Account user={session.profile.user} />;
}
