import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";
import { AuthError, getProfile } from "@/lib/auth/backend";
import { checkOrigin, clearSession, failure } from "@/lib/auth/session";
import { accountFetch, privateHeaders, readAccountBody, requireAccountToken } from "@/features/account/server";

const schema = z.object({
  currentPassword: z.string().max(128).refine(value => value.trim().length > 0),
  newPassword: z.string().min(12).max(128).refine(value => value.trim().length > 0),
}).strict();

export async function POST(request: NextRequest) {
  try {
    checkOrigin(request);
    const token = await requireAccountToken();
    const body = new TextDecoder().decode(await readAccountBody(request, 4096));
    let data: unknown;
    try { data = JSON.parse(body); } catch { throw new AuthError(400, "Solicitud inválida."); }
    const parsed = schema.safeParse(data);
    if (!parsed.success) throw new AuthError(400, "Revisa las contraseñas. La nueva debe tener entre 12 y 128 caracteres.");
    if (parsed.data.currentPassword === parsed.data.newPassword) throw new AuthError(409, "La nueva contraseña debe ser diferente.");
    // Distinguish an expired token from an incorrect current password.
    await getProfile(token);
    const response = await accountFetch("auth/change-password", token, "POST", JSON.stringify(parsed.data));
    if (response.status === 401) throw new AuthError(401, "La contraseña actual no es correcta o la sesión ha expirado.");
    if (response.status === 403) throw new AuthError(403, "No tienes permiso para realizar esta operación.");
    if (response.status === 409) throw new AuthError(409, "La nueva contraseña debe ser diferente.");
    if (response.status !== 204) throw new AuthError(502, "El servidor no confirmó el cambio de contraseña.");
    const result = new NextResponse(null, { status: 204, headers: privateHeaders });
    clearSession(result);
    return result;
  } catch (error) { return failure(error); }
}
