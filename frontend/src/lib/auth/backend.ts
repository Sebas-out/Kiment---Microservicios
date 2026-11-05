import "server-only";
import { z } from "zod";
import { accountProfileSchema } from "@/features/auth/contracts";

export class AuthError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}
type AuthEndpoint = "login" | "me" | "refresh" | "logout";

export async function backendFetch(path: string, options: RequestInit = {}) {
  const base = process.env.AUTH_API_URL;
  if (!base) throw new AuthError(503, "El servicio de autenticación no está disponible. Inténtalo más tarde.");
  const url = new URL(base);
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.search || url.hash) throw new Error("Invalid URL");
  const loopback = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
  // Allow local Spring Boot when verifying a production build on this computer.
  if (process.env.NODE_ENV === "production" && url.protocol !== "https:" && !loopback) throw new Error("HTTPS required");
  const headers = new Headers(options.headers);
  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
  return fetch(`${base.replace(/\/$/, "")}/api/v1/${path}`, {
    ...options, cache: "no-store", redirect: "error", signal: AbortSignal.timeout(10000),
    headers,
  });
}

export async function authRequest(path: AuthEndpoint, options: RequestInit = {}): Promise<unknown> {
  try {
    const response = await backendFetch(`auth/${path}`, options);
    if (!response.ok) {
      if (response.status === 401) throw new AuthError(401, path === "login" ? "Correo o contraseña incorrectos" : "Tu sesión ha expirado. Inicia sesión de nuevo.");
      if (response.status === 403) throw new AuthError(403, "No tienes permiso para realizar esta operación.");
      if (response.status === 429) throw new AuthError(429, "Demasiados intentos. Espera un momento y vuelve a intentarlo.");
      if (response.status === 400) throw new AuthError(400, "Revisa los datos de la solicitud.");
      throw new AuthError(502, "No se pudo completar la solicitud. Inténtalo más tarde.");
    }
    return response.status === 204 ? null : await response.json();
  } catch (error) {
    if (error instanceof AuthError) throw error;
    throw new AuthError(503, "El servicio de autenticación no está disponible. Inténtalo más tarde.");
  }
}

export async function getProfile(accessToken: string) {
  const data = await authRequest("me", { headers: { Authorization: `Bearer ${accessToken}` } });
  const profile = parseResponse(accountProfileSchema, data);
  if (!profile.user.enabled) throw new AuthError(401, "Tu sesión ha expirado. Inicia sesión de nuevo.");
  return profile;
}
export function parseResponse<T>(schema: z.ZodType<T>, data: unknown): T {
  const result = schema.safeParse(data);
  if (!result.success) throw new AuthError(502, "El servicio devolvió una respuesta no válida.");
  return result.data;
}
