import "server-only";
import { redirect } from "next/navigation";
import { cache } from "react";
import { cookies } from "next/headers";
import { AuthError, getProfile } from "./backend";
import { cookieNames } from "./session";
import type { AccountProfile } from "@/features/auth/contracts";

type CurrentSession =
  | { state: "authenticated"; profile: AccountProfile }
  | { state: "expired"; canRefresh: boolean }
  | { state: "forbidden" | "unavailable" };

// Request-scoped React cache, never a cross-user/global profile cache.
export const currentSession = cache(async (): Promise<CurrentSession> => {
  const jar = await cookies();
  const token = jar.get(cookieNames.access)?.value;
  const canRefresh = !!jar.get(cookieNames.refresh)?.value;
  if (!token) return { state: "expired", canRefresh };
  try { return { state: "authenticated", profile: await getProfile(token) }; }
  catch (error) {
    if (error instanceof AuthError && error.status === 401) return { state: "expired", canRefresh };
    if (error instanceof AuthError && error.status === 403) return { state: "forbidden" };
    return { state: "unavailable" };
  }
});

// Enforce this in pages as well: shared layouts persist across soft navigation.
export async function requireSession() {
  const session = await currentSession();
  if (session.state === "expired") redirect(session.canRefresh ? "/sesion" : "/?session=expired");
  return session;
}
