import { NextRequest } from "next/server";
import { authRequest } from "@/lib/auth/backend";
import { checkOrigin, clearSession, cookieNames, failure, json } from "@/lib/auth/session";
export async function POST(request: NextRequest) {
  try { checkOrigin(request); } catch (error) { return failure(error); }
  let revoked = true;
  const refreshToken = request.cookies.get(cookieNames.refresh)?.value;
  if (refreshToken) {
    try { await authRequest("logout", { method: "POST", body: JSON.stringify({ refreshToken }) }); }
    catch { revoked = false; }
  }
  // Always end the browser session after a valid same-origin logout request.
  const response = json({ ok: true, revoked });
  clearSession(response);
  return response;
}
