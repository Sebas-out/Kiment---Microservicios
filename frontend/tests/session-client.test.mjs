import { afterEach, test } from "node:test";
import assert from "node:assert/strict";
import { restoreSession, logoutSession, sessionWrite, SessionError } from "../src/features/auth/session-client.ts";
const originalFetch = globalThis.fetch;
const storageDescriptor = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
const navigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, "navigator");
afterEach(() => {
  globalThis.fetch = originalFetch;
  if (navigatorDescriptor) Object.defineProperty(globalThis, "navigator", navigatorDescriptor);
  else delete globalThis.navigator;
  if (storageDescriptor) Object.defineProperty(globalThis, "localStorage", storageDescriptor);
  else delete globalThis.localStorage;
});
function locks() {
  let previous = Promise.resolve();
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: { locks: {
    request(name, operation) { assert.equal(name, "kiments:session-refresh"); const next = previous.then(operation); previous = next.catch(() => {}); return next; },
  } } });
}
test("waiting tabs re-check /me so only one refresh rotation is sent", async () => {
  locks(); let authenticated = false; let rotations = 0;
  globalThis.fetch = async (url, options) => {
    if (url === "/api/auth/me") return authenticated ? Response.json({ user: {}, permissions: [] }) : Response.json({ error: "Expired" }, { status: 401 });
    assert.equal(url, "/api/auth/refresh"); assert.equal(options.method, "POST");
    assert.equal(options.credentials, "same-origin"); assert.equal(options.body, "{}");
    rotations++; authenticated = true; return Response.json({ ok: true });
  };
  await Promise.all([restoreSession(), restoreSession()]);
  assert.equal(rotations, 1);
});
test("403 never triggers refresh", async () => {
  locks(); let calls = 0;
  globalThis.fetch = async () => { calls++; return Response.json({ error: "Forbidden" }, { status: 403 }); };
  await assert.rejects(restoreSession(), error => error instanceof SessionError && error.status === 403);
  assert.equal(calls, 1);
});
test("without Web Locks renewal fails closed instead of racing refresh tokens", async () => {
  Object.defineProperty(globalThis, "navigator", { configurable: true, value: {} });
  globalThis.fetch = async () => { assert.fail("Must not make a network request"); };
  await assert.rejects(restoreSession(), error => error instanceof SessionError && error.status === 401);
});
test("logout distinguishes local closure from remote revocation failure", async () => {
  locks();
  globalThis.fetch = async (url, options) => {
    assert.equal(url, "/api/auth/logout"); assert.equal(options.method, "POST");
    return Response.json({ ok: true, revoked: false });
  };
  assert.deepEqual(await logoutSession(), { revoked: false });
});
test("a CSRF rejection cannot be reported as successful logout", async () => {
  locks();
  globalThis.fetch = async () => Response.json({ error: "Not allowed" }, { status: 403 });
  await assert.rejects(logoutSession(), error => error instanceof SessionError && error.status === 403);
});
test("logout waits for a refresh response before deleting cookies", async () => {
  locks();
  let release, start;
  const started = new Promise(resolve => { start = resolve; });
  const delayed = new Promise(resolve => { release = resolve; });
  const order = [];
  globalThis.fetch = async url => {
    if (url.endsWith("/me")) return Response.json({}, { status: 401 });
    if (url.endsWith("/refresh")) {
      order.push("refresh started");
      start();
      await delayed;
      order.push("refresh completed");
      return Response.json({ ok: true });
    }
    order.push("logout");
    return Response.json({ ok: true, revoked: true });
  };
  const refresh = restoreSession();
  await started;
  const logout = logoutSession();
  await Promise.resolve();
  assert.deepEqual(order, ["refresh started"]);
  release();
  await Promise.all([refresh, logout]);
  assert.deepEqual(order, ["refresh started", "refresh completed", "logout"]);
});
test("a queued operation from the old identity cannot mutate the new session", async () => {
  locks();
  let value = "";
  Object.defineProperty(globalThis, "localStorage", { configurable: true, value: {
    getItem: () => value, setItem: (_key, next) => { value = next; },
  } });
  const login = sessionWrite(async () => {}, true);
  globalThis.fetch = async () => assert.fail("Stale request reached the network");
  const old = restoreSession();
  await login;
  await assert.rejects(old, { status: 409 });
});
test("terminal refresh rejection clears once, without retrying", async () => {
  locks();
  const calls = [];
  globalThis.fetch = async url => {
    calls.push(url);
    return url.endsWith("/logout") ? Response.json({ ok: true, revoked: true }) : Response.json({}, { status: 401 });
  };
  await assert.rejects(restoreSession(), { status: 401 });
  assert.deepEqual(calls, ["/api/auth/me", "/api/auth/refresh", "/api/auth/logout"]);
});
