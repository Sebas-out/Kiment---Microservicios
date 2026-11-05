"use client";

import { restoreSession, sessionGeneration, sessionWrite, SessionError } from "@/features/auth/session-client";
import { avatarPath } from "./avatar-contract";

export async function accountMutation(path: "avatar" | "password", method: "POST" | "DELETE", body?: BodyInit) {
  if (path === "password") return sessionWrite(() => sendMutation(path, method, body), true);
  return sendMutation(path, method, body);
}

async function sendMutation(path: "avatar" | "password", method: "POST" | "DELETE", body?: BodyInit) {
  const generation = sessionGeneration();
  let response: Response;
  try {
    response = await fetch(path === "avatar" ? avatarPath : "/api/account/password", {
      method, body, credentials: "same-origin", cache: "no-store",
      ...(path === "avatar" ? { signal: AbortSignal.timeout(15000) } : {}),
      ...(path === "password" ? { headers: { "Content-Type": "application/json" } } : {}),
    });
  } catch { throw new Error("No se pudo conectar con el servidor. Intenta nuevamente."); }
  if (path === "avatar" && response.status === 401) {
    try { await restoreSession(generation); }
    catch (error) {
      if (error instanceof SessionError && error.status !== 401) throw error;
      throw new Error("Tu sesión ha expirado. Inicia sesión nuevamente.");
    }
    throw new Error("Sesión renovada. Confirma de nuevo para continuar.");
  }
  if (sessionGeneration() !== generation) throw new Error("La sesión cambió. Recarga la página.");
  const data = response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) throw new Error(typeof data?.error === "string" ? data.error : "No se pudo completar la operación.");
  if (path === "avatar" && method === "POST" ? response.status !== 200 || data?.hasProfileImage !== true : response.status !== 204) {
    throw new Error("El servidor no confirmó la operación.");
  }
}
