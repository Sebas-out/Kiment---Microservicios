import { NextRequest } from "next/server";
import { z } from "zod";
import { readAccountBody } from "@/features/account/server";
import { accessToken, checkOrigin, failure, json } from "@/lib/auth/session";
import { AuthError, backendFetch, getProfile } from "@/lib/auth/backend";
import { operation, permitted, backendProblem } from "@/features/users/operations";

async function handle(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  try {
    const mutation = request.method !== "GET";
    if (mutation) checkOrigin(request);
    const token = await accessToken();
    if (!token) throw new AuthError(401, "Tu sesión ha expirado. Inicia sesión nuevamente.");
    const profile = await getProfile(token);
    const op = operation(request.method, (await context.params).path, request.nextUrl.searchParams);
    if (!op) return json({ error: "Operación no disponible." }, 404);
    if (!permitted(profile.user.role, profile.user.enabled, profile.permissions, op.permission)) throw new AuthError(403, "No tienes permiso para realizar esta operación.");
    let body: string | undefined;
    if (op.body) {
      const raw = new TextDecoder().decode(await readAccountBody(request, 16384));
      let value: unknown;
      try { value = JSON.parse(raw); } catch { return json({ error: "Solicitud inválida." }, 400); }
      body = JSON.stringify(op.body.parse(value));
    }
    let response: Response;
    try { response = await backendFetch(op.path, { method: request.method, headers: { Authorization: `Bearer ${token}` }, ...(body ? { body } : {}) }); }
    catch { return json({ error: "No se pudo conectar con el servidor. Intenta nuevamente." }, 503); }
    const data: unknown = await response.json().catch(() => null);
    if (!response.ok) return json(backendProblem(response.status, data), response.status >= 500 ? 502 : response.status);
    const parsed = op.response.safeParse(data);
    if (!parsed.success) return json({ error: "El servidor devolvió una respuesta no válida. Actualiza la lista antes de volver a guardar." }, 502);
    return json(parsed.data, response.status);
  } catch (error) {
    if (error instanceof z.ZodError) {
      const fields = Object.fromEntries(error.issues.filter(i => typeof i.path[0] === "string").map(i => [i.path[0], i.message]));
      return json({ error: "Revisa los campos de la solicitud.", fields }, 400);
    }
    return failure(error);
  }
}
export const GET = handle;
export const POST = handle;
export const PATCH = handle;
