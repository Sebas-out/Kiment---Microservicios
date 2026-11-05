import type { Metadata } from "next";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { cookieNames } from "@/lib/auth/session";
import { SessionRecovery } from "@/features/auth/components/session-recovery";
export const metadata: Metadata = { title: "Renovar sesión | KIMENTS POS" };
export default async function SessionPage() {
  if (!(await cookies()).get(cookieNames.refresh)?.value) redirect("/?session=expired");
  return <SessionRecovery />;
}
