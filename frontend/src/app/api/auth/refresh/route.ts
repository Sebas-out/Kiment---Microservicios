import { NextRequest } from "next/server";
import { AuthError, getProfile } from "@/lib/auth/backend";
import { rotateSession } from "@/lib/auth/rotation";
import { checkOrigin, cookieNames, failure, json, setSession } from "@/lib/auth/session";
export async function POST(request: NextRequest) {
  try {
    checkOrigin(request);
    const refreshToken = request.cookies.get(cookieNames.refresh)?.value;
    if (!refreshToken) throw new AuthError(401, "Inicia sesión para continuar.");
    const response = json({ ok: true });
    const tokens = await rotateSession(refreshToken);
    await getProfile(tokens.accessToken);
    setSession(response, tokens);
    return response;
  } catch (error) {
    return failure(error);
  }
}
