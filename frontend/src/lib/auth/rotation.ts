import "server-only";
import { createHash } from "node:crypto";
import { tokenSchema, type TokenResponse } from "@/features/auth/contracts";
import { authRequest, parseResponse } from "./backend";

// Coalesce overlapping requests in this Next.js process. Browser Web Locks also
// serialize tabs; multiple deployment instances need a shared server-side lock.
const inFlight = new Map<string, Promise<TokenResponse>>();
export async function rotateSession(refreshToken: string): Promise<TokenResponse> {
  const key = createHash("sha256").update(refreshToken).digest("hex");
  const pending = inFlight.get(key);
  if (pending) return pending;
  const operation = authRequest("refresh", { method: "POST", body: JSON.stringify({ refreshToken }) })
    .then(data => parseResponse(tokenSchema, data));
  inFlight.set(key, operation);
  try { return await operation; }
  finally { if (inFlight.get(key) === operation) inFlight.delete(key); }
}
