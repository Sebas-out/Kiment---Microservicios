import { z } from "zod";
import { accountFetch, privateAvatarResponse, requireAccountToken } from "@/features/account/server";
import { canReadUsers } from "@/features/auth/contracts";
import { AuthError, getProfile } from "@/lib/auth/backend";
import { failure } from "@/lib/auth/session";

export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const token = await requireAccountToken();
    const profile = await getProfile(token);
    if (!canReadUsers(profile)) throw new AuthError(403, "No tienes permiso para consultar esta fotografía.");
    const parsed = z.uuid().safeParse((await params).id);
    if (!parsed.success) throw new AuthError(404, "La fotografía no está disponible.");
    return privateAvatarResponse(await accountFetch(`users/${parsed.data}/avatar`, token, "GET"));
  } catch (error) { return failure(error); }
}
