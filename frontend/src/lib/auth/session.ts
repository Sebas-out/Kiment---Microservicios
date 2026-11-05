import "server-only";
import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";
import { AuthError } from "./backend";
import type { TokenResponse } from "@/features/auth/contracts";
const production = process.env.NODE_ENV === "production";
export const cookieNames = {
  access: production ? "__Host-kiments-access" : "kiments-access",
  refresh: production ? "__Host-kiments-refresh" : "kiments-refresh",
};
const options = { httpOnly: true, secure: production, sameSite: "strict" as const, path: "/" };
export function setSession(response: NextResponse, tokens: TokenResponse) {
  response.cookies.set(cookieNames.access, tokens.accessToken, { ...options, maxAge: tokens.expiresIn });
  // The actual API does not return refresh expiry. Keep it as a browser-session
  // cookie; Spring Boot enforces its absolute family expiry, including rotation.
  response.cookies.set(cookieNames.refresh, tokens.refreshToken, options);
}
export function clearSession(response: NextResponse) {
  for (const name of Object.values(cookieNames)) response.cookies.set(name, "", { ...options, maxAge: 0 });
}
export async function accessToken() { return (await cookies()).get(cookieNames.access)?.value; }
export function checkOrigin(request: NextRequest, contentType: RegExp | null = /^application\/json(?:\s*;|$)/i) {
  const configured = process.env.APP_ORIGIN;
  if (production && !configured) throw new AuthError(503, "El servicio de autenticación no está disponible.");
  let expected: string;
  try { expected = new URL(configured || request.url).origin; }
  catch { throw new AuthError(503, "El servicio de autenticación no está disponible."); }
  if (request.headers.get("origin") !== expected || request.headers.get("sec-fetch-site") === "cross-site")
    throw new AuthError(403, "Solicitud no permitida. Recarga la página e inténtalo de nuevo.");
  if (contentType && !contentType.test(request.headers.get("content-type") || ""))
    throw new AuthError(415, "Formato de solicitud no permitido.");
}
export function json(data: unknown, status = 200) {
  return NextResponse.json(data, { status, headers: { "Cache-Control": "no-store", "Vary": "Cookie" } });
}
export function failure(error: unknown) {
  return error instanceof AuthError ? json({ error: error.message }, error.status)
    : json({ error: "No se pudo completar la solicitud." }, 500);
}
