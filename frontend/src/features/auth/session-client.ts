"use client";

export const sessionSignal = "kiments:session-change";


// An opaque UI generation only. Authentication stays entirely in HttpOnly cookies.
export function sessionGeneration() {
  try { return localStorage.getItem(sessionSignal) ?? ""; } catch { return ""; }
}
function publishSessionChange() {
  try { localStorage.setItem(sessionSignal, crypto.randomUUID()); } catch { /* focus revalidation remains available */ }
}

export async function sessionWrite<T>(operation: () => Promise<T>, announce = false, expected = sessionGeneration()): Promise<T> {
  if (!globalThis.navigator?.locks) throw new SessionError(401, "Este navegador no puede coordinar el acceso de forma segura.");
  return navigator.locks.request("kiments:session-refresh", async () => {
    if (sessionGeneration() !== expected) throw new SessionError(409, "La sesión cambió en otra pestaña. Recarga la página.");
    const result = await operation();
    if (announce) publishSessionChange();
    return result;
  });
}

export class SessionError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}
async function request(path: "me" | "refresh" | "logout", method: "GET" | "POST") {
  let response: Response;
  try {
    response = await fetch(`/api/auth/${path}`, {
      method, credentials: "same-origin", cache: "no-store",
      ...(method === "POST" ? { headers: { "Content-Type": "application/json" }, body: "{}" } : {}),
      ...(method === "GET" ? { signal: AbortSignal.timeout(15000) } : {}),
    });
  } catch { throw new SessionError(503, "No se pudo conectar con el servidor. Intenta nuevamente."); }
  const data: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const error = data && typeof data === "object" && "error" in data && typeof data.error === "string"
      ? data.error : "No se pudo completar la solicitud.";
    throw new SessionError(response.status, error);
  }
  return data;
}

export async function restoreSession(expected = sessionGeneration()) {
  await sessionWrite(async () => {
    // Another tab may already have rotated the shared HttpOnly cookies.
    try { await request("me", "GET"); return; }
    catch (error) { if (!(error instanceof SessionError) || error.status !== 401) throw error; }
    try { await request("refresh", "POST"); }
    catch (error) {
      if (error instanceof SessionError && [400, 401].includes(error.status)) {
        await request("logout", "POST").catch(() => undefined);
        publishSessionChange();
        if (typeof window !== "undefined") window.dispatchEvent(new Event("kiments:session-ended"));
      }
      throw error;
    }
  }, false, expected);
}
export async function logoutSession(): Promise<{ revoked: boolean }> {
  return sessionWrite(async () => {
    const data = await request("logout", "POST");
    if (!data || typeof data !== "object" || !("ok" in data) || data.ok !== true || !("revoked" in data) || typeof data.revoked !== "boolean") {
      throw new SessionError(502, "No se pudo confirmar el cierre de sesión. Inténtalo nuevamente.");
    }
    return { revoked: data.revoked };
  }, true);
}

export async function readSessionIdentity(): Promise<string | null> {
  const data = await request("me", "GET");
  if (!data || typeof data !== "object" || !("user" in data)) return null;
  const user = data.user;
  return user && typeof user === "object" && "id" in user && typeof user.id === "string" ? user.id : null;
}
