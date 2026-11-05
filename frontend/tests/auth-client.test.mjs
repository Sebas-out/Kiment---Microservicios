import { beforeEach, afterEach, test } from "node:test";
import assert from "node:assert/strict";
import { sourceModule } from "./users-test-loader.mjs";
const { login, loginMessages } = await import(await sourceModule("src/features/auth/client.ts"));
import { notify } from "../src/lib/notifications.ts";
import { toast } from "sonner";

const originalFetch = globalThis.fetch;
const credentials = { email: "prueba@example.com", password: "solo-prueba" };
const navigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, "navigator");
beforeEach(() => Object.defineProperty(globalThis, "navigator", {
  configurable: true, value: { locks: { request(_name, operation) { return operation(); } } },
}));
afterEach(() => {
  globalThis.fetch = originalFetch;
  if (navigatorDescriptor) Object.defineProperty(globalThis, "navigator", navigatorDescriptor);
  else delete globalThis.navigator;
  toast.dismiss();
});
const reply = (status, data) => { globalThis.fetch = async () => Response.json(data, { status }); };

test("401 uses the requested credentials message", async () => {
  reply(401, { error: "Upstream message" });
  await assert.rejects(login(credentials), { message: loginMessages.invalidCredentials });
});
test("network failure uses the connection message", async () => {
  globalThis.fetch = async () => { throw new TypeError("Failed to fetch"); };
  await assert.rejects(login(credentials), { message: loginMessages.connection });
});
test("unavailable backend uses the connection message", async () => {
  reply(503, { error: "Service unavailable" });
  await assert.rejects(login(credentials), { message: loginMessages.connection });
});
test("rate limit keeps the controlled BFF explanation", async () => {
  reply(429, { error: "Espera un momento." });
  await assert.rejects(login(credentials), { message: "Espera un momento." });
});
test("HTTP 200 without confirmation never counts as success", async () => {
  reply(200, { ok: false });
  await assert.rejects(login(credentials), { message: loginMessages.server });
});
test("HTML from a proxy never counts as successful authentication", async () => {
  globalThis.fetch = async () => new Response("<html>Proxy</html>", { status: 200 });
  await assert.rejects(login(credentials), { message: loginMessages.server });
});
test("valid BFF confirmation completes login without exposing tokens", async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, "/api/auth/login");
    assert.equal(options.credentials, "same-origin");
    assert.equal(options.method, "POST");
    assert.deepEqual(JSON.parse(options.body), credentials);
    return Response.json({ ok: true });
  };
  await login(credentials);
});
test("identical notifications are deduplicated by Sonner", () => {
  const message = "Error de prueba";
  notify.error(message);
  notify.error(message);
  const active = toast.getToasts().filter(item => item.title === message);
  assert.equal(active.length, 1);
  assert.equal(active[0].duration, 4000);
});
test("loading and its outcome share a single toast", () => {
  notify.loading("Guardando…", { id: "test:operation" });
  notify.success("Guardado", { id: "test:operation" });
  const active = toast.getToasts().filter(item => item.id === "test:operation");
  assert.equal(active.length, 1);
  assert.equal(active[0].type, "success");
  assert.equal(active[0].duration, 4000);
});
