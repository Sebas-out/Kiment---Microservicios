import { accessToken, failure, json } from "@/lib/auth/session";
import { AuthError, getProfile } from "@/lib/auth/backend";
import { profileSchema } from "@/features/auth/contracts";
export async function GET() {
  try {
    const token = await accessToken();
    if (!token) throw new AuthError(401, "Inicia sesión para continuar.");
    return json(profileSchema.parse(await getProfile(token)));
  } catch (error) { return failure(error); }
}
