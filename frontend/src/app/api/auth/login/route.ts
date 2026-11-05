import { readAccountBody } from "@/features/account/server";
import { NextRequest } from "next/server";
import { loginSchema } from "@/features/auth/schema";
import { tokenSchema } from "@/features/auth/contracts";
import { authRequest, getProfile, parseResponse } from "@/lib/auth/backend";
import { checkOrigin, failure, json, setSession } from "@/lib/auth/session";
export async function POST(request: NextRequest) {
  try {
    checkOrigin(request);
    const bytes = await readAccountBody(request, 4096);
    let data: unknown;
    try { data = JSON.parse(new TextDecoder().decode(bytes)); } catch { data = null; }
    const input = loginSchema.safeParse(data);
    if (!input.success) return json({ error: "Revisa el correo electrónico y la contraseña." }, 400);
    const tokens = parseResponse(tokenSchema, await authRequest("login", { method: "POST", body: JSON.stringify(input.data) }));
    try { await getProfile(tokens.accessToken); }
    catch (error) {
      // Do not leave a newly issued refresh session orphaned after a bad profile.
      await authRequest("logout", { method: "POST", body: JSON.stringify({ refreshToken: tokens.refreshToken }) }).catch(() => undefined);
      throw error;
    }
    const response = json({ ok: true });
    setSession(response, tokens);
    return response;
  } catch (error) { return failure(error); }
}
