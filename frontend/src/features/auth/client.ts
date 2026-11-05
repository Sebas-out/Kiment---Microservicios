import type { LoginInput } from "./schema";
import { sessionWrite } from "./session-client";

export const loginMessages = {
  invalidCredentials: "Correo o contraseña incorrectos",
  connection: "No se pudo conectar con el servidor. Intenta nuevamente.",
  success: "Sesión iniciada correctamente",
  server: "No se pudo iniciar sesión. Inténtalo nuevamente.",
} as const;

export function login(input: LoginInput): Promise<void> {
  return sessionWrite(() => submitLogin(input), true);
}

async function submitLogin(input: LoginInput): Promise<void> {
  let response: Response;
  try {
    response = await fetch("/api/auth/login", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input), credentials: "same-origin",
    });
  } catch {
    throw new Error(loginMessages.connection);
  }

  const data: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401) throw new Error(loginMessages.invalidCredentials);
    if (response.status === 503 || response.status === 504) throw new Error(loginMessages.connection);
    const message = data && typeof data === "object" && "error" in data && typeof data.error === "string"
      ? data.error : loginMessages.server;
    throw new Error(message);
  }
  // A successful HTTP response alone is insufficient (e.g. a proxy returning HTML).
  if (!data || typeof data !== "object" || !("ok" in data) || data.ok !== true) {
    throw new Error(loginMessages.server);
  }
}
