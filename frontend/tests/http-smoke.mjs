import assert from "node:assert/strict";

const base = process.env.FRONTEND_URL || "http://localhost:3000";
const origin = new URL(base).origin;
let checks = 0;
async function request(path, { method = "GET", data, headers = {} } = {}) {
  return fetch(new URL(path, base), { method, redirect: "manual", headers, ...(data !== undefined ? { body: data } : {}) });
}
async function status(path, expected, options) {
  const response = await request(path, options);
  assert.equal(response.status, expected, `${path}: expected ${expected}, got ${response.status}`);
  if (path.startsWith("/api/")) assert.match(response.headers.get("cache-control") || "", /no-store/);
  checks++; console.log(`PASS ${path}: ${expected}`); return response;
}
const jsonHeaders = { Origin: origin, "Content-Type": "application/json" };
await status("/", 200);
for (const path of ["/dashboard", "/usuarios", "/sesion"]) {
  const response = await status(path, 307);
  assert.equal(new URL(response.headers.get("location"), base).pathname, "/");
}
await status("/api/auth/me", 401);
await status("/api/auth/login", 400, { method: "POST", data: JSON.stringify({ email: "invalid", password: "" }), headers: jsonHeaders });
await status("/api/auth/login", 400, { method: "POST", data: JSON.stringify({ email: "fixture@example.com", password: "p".repeat(129) }), headers: jsonHeaders });
await status("/api/auth/login", 400, { method: "POST", data: "{bad json", headers: jsonHeaders });
await status("/api/auth/login", 403, { method: "POST", data: "{}", headers: { ...jsonHeaders, Origin: "https://untrusted.example" } });
await status("/api/auth/login", 403, { method: "POST", data: "{}", headers: { "Content-Type": "application/json" } });
await status("/api/auth/login", 415, { method: "POST", data: "{}", headers: { Origin: origin, "Content-Type": "application/jsonp" } });
const refresh = await status("/api/auth/refresh", 401, { method: "POST", data: "{}", headers: jsonHeaders });
assert.ok(refresh.headers.getSetCookie().every(cookie => /HttpOnly/i.test(cookie)));
const rejectedLogout = await status("/api/auth/logout", 403, { method: "POST", data: "{}", headers: { ...jsonHeaders, Origin: "https://untrusted.example" } });
assert.equal(rejectedLogout.headers.getSetCookie().length, 0);
const logout = await status("/api/auth/logout", 200, { method: "POST", data: "{}", headers: jsonHeaders });
assert.deepEqual(await logout.json(), { ok: true, revoked: true });
const cleared = logout.headers.getSetCookie();
assert.equal(cleared.length, 2);
for (const cookie of cleared) {
  assert.match(cookie, /HttpOnly/i); assert.match(cookie, /SameSite=Strict/i); assert.match(cookie, /Max-Age=0/i);
  if (cookie.startsWith("__Host-")) { assert.match(cookie, /Secure/i); assert.match(cookie, /Path=\//i); }
}
const forged = await request("/dashboard", { headers: { Cookie: "kiments-access=invalid; __Host-kiments-access=invalid" } });
assert.ok([200, 307].includes(forged.status));
assert.ok(!(await forged.text()).includes('class="admin-shell"'), "An invented cookie must never render an authenticated shell");
checks++; console.log("PASS forged access cookie denied");
const pending = await status("/dashboard", 307, { headers: { Cookie: `kiments-refresh=${"r".repeat(43)}; __Host-kiments-refresh=${"r".repeat(43)}` } });
assert.equal(new URL(pending.headers.get("location"), base).pathname, "/sesion");
console.log(`${checks} HTTP checks passed. No credentials, user endpoints, or refresh rotations were used.`);
