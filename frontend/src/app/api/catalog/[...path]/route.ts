import { NextRequest } from "next/server";
import { AuthError, getProfile } from "@/lib/auth/backend";
import { accessToken, checkOrigin, failure, json } from "@/lib/auth/session";
import { readAccountBody, privateHeaders } from "@/features/account/server";

import { catalogPermission } from "@/features/products/catalog-operations";

async function handle(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  try {
    if (request.method !== "GET") checkOrigin(request, request.headers.get("Content-Type")?.startsWith("multipart/form-data") ? /^multipart\/form-data(?:\s*;|$)/i : /^application\/json(?:\s*;|$)/i);
    const path = (await context.params).path.join("/");
    const permission = catalogPermission(request.method, path);
    if (!permission) return json({ error: "Operación no disponible." }, 404);
    const token = await accessToken();
    if (!token) throw new AuthError(401, "Tu sesión expiró.");
    const profile = await getProfile(token);
    if (!profile.permissions.includes(permission)) throw new AuthError(403, "No tienes permiso para esta operación.");
    const base = new URL(process.env.CATALOG_API_URL ?? "http://localhost:8082");
    if (!["http:", "https:"].includes(base.protocol) || base.username || base.password || base.search || base.hash) throw new Error("Configuración inválida");
    if (process.env.NODE_ENV === "production" && base.protocol !== "https:" && !["localhost", "127.0.0.1", "[::1]"].includes(base.hostname)) throw new Error("HTTPS requerido");
    const url = new URL("/api/v1/" + path, base);
    url.search = request.nextUrl.search;
    const headers = new Headers({ Authorization: "Bearer " + token });
    const idempotency = request.headers.get("Idempotency-Key");
    if (idempotency) {
      if (!/^[a-zA-Z0-9-]{1,100}$/.test(idempotency)) return json({ error: "Clave de operación inválida." }, 400);
      headers.set("Idempotency-Key", idempotency);
    }
    let body: BodyInit | undefined;
    if (request.method !== "GET") {
      const multipart = request.headers.get("Content-Type")?.startsWith("multipart/form-data");
      const bytes = await readAccountBody(request, multipart ? 6 * 1024 * 1024 : 256 * 1024);
      if (multipart) {
        const form = await new Response(bytes, { headers: { "Content-Type": request.headers.get("Content-Type")! } }).formData();
        body = form;
      } else if (bytes.byteLength) {
        try { body = JSON.stringify(JSON.parse(new TextDecoder().decode(bytes))); }
        catch { return json({ error: "El cuerpo JSON no es válido." }, 400); }
        headers.set("Content-Type", "application/json");
      }
    }
    let response: Response;
    try { response = await fetch(url, { method: request.method, headers, body, cache: "no-store", redirect: "error", signal: AbortSignal.timeout(25000) }); }
    catch { return json({ error: "No se pudo conectar con Catálogo e Inventario." }, 503); }
    if (!response.ok) {
      const problem = (await response.json().catch(() => ({}))) ?? {};
      return json({ error: response.status >= 500 ? `Error ${response.status} de Catálogo e Inventario al consultar ${path}.` : problem.detail ?? problem.message ?? "No se pudo completar la operación.", fields: problem.fieldErrors, reference: typeof problem.correlationId === "string" ? problem.correlationId : undefined }, response.status);
    }
    if (response.status === 204) return new Response(null, { status: 204, headers: privateHeaders });
    const type = response.headers.get("Content-Type") ?? "";
    if (/^image\/(png|jpeg|webp)/.test(type) || type.includes("spreadsheetml")) {
      return new Response(response.body, { headers: { ...privateHeaders, "Content-Type": type, ...(type.includes("spreadsheetml") ? { "Content-Disposition": 'attachment; filename="inventario.xlsx"' } : {}) } });
    }
    return json(await response.json(), response.status);
  } catch (error) { return failure(error); }
}
export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
