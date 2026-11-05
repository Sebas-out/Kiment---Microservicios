import "server-only";
import { AuthError, backendFetch } from "@/lib/auth/backend";
import { isAvatarType } from "./avatar-contract";
import { accessToken } from "@/lib/auth/session";

export const privateHeaders = { "Cache-Control": "private, no-store", "Vary": "Cookie", "X-Content-Type-Options": "nosniff", "Cross-Origin-Resource-Policy": "same-origin" };

export async function requireAccountToken() {
  const token = await accessToken();
  if (!token) throw new AuthError(401, "Tu sesión ha expirado. Inicia sesión nuevamente.");
  return token;
}

export async function accountFetch(path: "users/me/avatar" | `users/${string}/avatar` | "auth/change-password", token: string, method: string, body?: BodyInit) {
  try {
    return await backendFetch(path, { method, headers: { Authorization: `Bearer ${token}` }, body });
  } catch {
    throw new AuthError(503, "No se pudo conectar con el servidor. Intenta nuevamente.");
  }
}

export function avatarResponseError(status: number) {
  switch (status) {
    case 400: return new AuthError(400, "El servidor no pudo procesar la imagen. Prueba con otro JPG o PNG.");
    case 401: return new AuthError(401, "Tu sesión ha expirado. Inicia sesión nuevamente.");
    case 403: return new AuthError(403, "No tienes permiso para realizar esta operación.");
    case 404: return new AuthError(404, "La fotografía no está disponible.");
    case 413: return new AuthError(413, "La foto excede el tamaño permitido.");
    case 429: return new AuthError(429, "Demasiados intentos. Vuelve a intentarlo en un momento.");
    default: return new AuthError(502, "El servidor no pudo completar la operación de fotografía.");
  }
}

export function privateAvatarResponse(response: Response) {
  if (!response.ok) throw avatarResponseError(response.status);
  const type = response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() ?? "";
  if (response.status !== 200 || !response.body || !isAvatarType(type)) {
    throw new AuthError(502, "El servidor devolvió una imagen no válida.");
  }
  return new Response(response.body, { headers: { ...privateHeaders, "Content-Type": type } });
}

// Bound actual bytes even if Content-Length is missing or inaccurate.
export async function readAccountBody(request: Request, limit: number) {
  if (Number(request.headers.get("content-length")) > limit) throw new AuthError(413, "La solicitud supera el tamaño permitido.");
  const reader = request.body?.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  if (reader) {
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.byteLength;
        if (size > limit) {
          void reader.cancel().catch(() => {});
          throw new AuthError(413, "La solicitud supera el tamaño permitido.");
        }
        chunks.push(value);
      }
    } finally { reader.releaseLock(); }
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
}
