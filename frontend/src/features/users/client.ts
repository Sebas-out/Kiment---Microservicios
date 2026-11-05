"use client";
import { restoreSession, sessionGeneration, SessionError } from "@/features/auth/session-client";

export class UsersError extends Error {
  constructor(public status: number, message: string, public fields: Record<string,string> = {}) { super(message); }
}
// Same-origin BFF only; the existing session coordinator owns token refresh.
export async function usersRequest<T>(path: string, method = "GET", body?: unknown, signal?: AbortSignal): Promise<T> {
  const generation = sessionGeneration();
  const send = () => fetch(`/api/management/${path}`, { method, credentials: "same-origin", cache: "no-store", signal: signal ?? AbortSignal.timeout(15000), ...(body !== undefined ? { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {}) });
  let response: Response;
  try {
    response = await send();
    if (response.status === 401) {
      await restoreSession(generation);
      // Read requests can be replayed. Writes require an explicit retry by the operator.
      if (method === "GET") response = await send();
      else throw new UsersError(401, "Sesión renovada. Revisa los datos y vuelve a guardar.");
    }
  } catch (error) {
    if (error instanceof UsersError) throw error;
    if (signal?.aborted) throw error;
    if (error instanceof SessionError) throw new UsersError(error.status, error.message);
    throw new UsersError(503, "No se pudo conectar con el servidor. Intenta nuevamente.");
  }
  const data = await response.json().catch(() => null);
  if (sessionGeneration() !== generation) throw new UsersError(409, "La sesión cambió. Recarga la página.");
  if (!response.ok) throw new UsersError(response.status, data?.error ?? "No se pudo completar la operación.", data?.fields ?? {});
  return data as T;
}
