import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { startFixture } from "./management-fixture.mjs";

const fixture = await startFixture();
const { origin } = fixture;
const endpoint = "/api/account/avatar";
const cookie = "__Host-kiments-access=admin";
const jpeg = await readFile(new URL("../public/images/store-02.jpg", import.meta.url));
const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aQ2kAAAAASUVORK5CYII=", "base64");
let passed = 0;
const check = (label, fn) => { fn(); passed++; console.log("✓ " + label); };
const request = (path = endpoint, options = {}) => fetch(origin + path, { redirect: "manual", ...options, headers: { cookie, origin, ...options.headers } });
const upload = (bytes, type, name, headers = {}) => {
  const body = new FormData();
  body.set("file", new Blob([bytes], { type }), name);
  return request(endpoint, { method: "POST", body, headers });
};
const getMe = async () => (await request("/api/auth/me")).json();
const page = async () => (await request("/mi-cuenta")).text();

try {
  let r = await request(endpoint, { headers: { cookie: "" } });
  check("image route rejects anonymous requests", () => assert.equal(r.status, 401));
  r = await request("/mi-cuenta", { headers: { cookie: "" } });
  check("account page rejects anonymous navigation", () => assert.match(r.headers.get("location"), /session=expired/));
  r = await request();
  check("user without image receives 404 and no-store", () => { assert.equal(r.status, 404); assert.match(r.headers.get("cache-control"), /no-store/); });
  const streamed = bytes => new ReadableStream({ start(controller) {
    controller.enqueue(new TextEncoder().encode("é".repeat(bytes)));
    controller.close();
  } });
  r = await request("/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" },
    body: streamed(2500), duplex: "half" });
  check("login limits the actual stream in bytes", () => assert.equal(r.status, 413));
  r = await request("/api/management/users", { method: "POST", headers: { "Content-Type": "application/json" },
    body: streamed(9000), duplex: "half" });
  check("management limits the actual stream in bytes", () => assert.equal(r.status, 413));
  r = await request("/api/auth/login", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: "qa@example.invalid", password: "fixture-password" }) });
  const cookies = r.headers.getSetCookie();
  check("production login uses host-only secure HttpOnly cookies", () => {
    assert.equal(r.status, 200);
    assert.equal(cookies.length, 2);
    for (const value of cookies) {
      assert.match(value, /^__Host-/);
      assert.match(value, /Secure/i);
      assert.match(value, /HttpOnly/i);
      assert.match(value, /SameSite=Strict/i);
      assert.match(value, /Path=\//i);
      assert.doesNotMatch(value, /Domain=/i);
    }
    assert.match(cookies.find(value => value.startsWith("__Host-kiments-access=")), /Max-Age=900/);
    assert.doesNotMatch(cookies.find(value => value.startsWith("__Host-kiments-refresh=")), /Expires|Max-Age/i);
  });
  r = await request("/api/auth/refresh", { method: "POST", headers: {
    "Content-Type": "application/json", cookie: "__Host-kiments-refresh=" + "r".repeat(43),
  }, body: "{}" });
  const rejected = await r.json();
  check("generic 401 from upstream carries no invented reason or delayed cookie deletion", () => {
    assert.equal(r.status, 401); assert.equal(r.headers.getSetCookie().length, 0);
    assert.deepEqual(Object.keys(rejected), ["error"]);
  });
  const noPhoto = await page();
  check("initials render without any avatar URL", () => { assert.match(noPhoto, /Resumen del perfil/); assert.match(noPhoto, /class="admin-avatar"[^>]*><span>PA/); assert.doesNotMatch(noPhoto, /src="\/api\/account\/avatar/); });
  r = await upload(jpeg, "image/jpeg", "foto.jpg");
  const jpgResult = await r.json();
  check("JPEG multipart upload is confirmed without exposing storage key", () => { assert.equal(r.status, 200); assert.deepEqual(jpgResult, { hasProfileImage: true }); });
  r = await request();
  const jpgBytes = Buffer.from(await r.arrayBuffer());
  check("GET streams correct JPEG bytes with private no-store headers", () => {
    assert.equal(r.status, 200); assert.equal(r.headers.get("content-type"), "image/jpeg");
    assert.equal(r.headers.get("cache-control"), "private, no-store");
    assert.equal(r.headers.get("x-content-type-options"), "nosniff");
    assert.deepEqual(jpgBytes, jpeg);
  });
  const worker = "/api/management/users/" + fixture.actorId + "/avatar";
  r = await request(worker);
  const workerBytes = Buffer.from(await r.arrayBuffer());
  check("authorized worker photo returns private binary", () => {
    assert.equal(r.status, 200); assert.equal(r.headers.get("cache-control"), "private, no-store");
    assert.deepEqual(workerBytes, jpeg);
  });
  r = await request(worker, { headers: { cookie: "" } });
  check("anonymous worker photo request returns 401", () => assert.equal(r.status, 401));
  r = await request(worker, { headers: { cookie: "__Host-kiments-access=sales" } });
  check("operational account cannot use the administrative avatar route", () => assert.equal(r.status, 403));
  r = await request("/api/management/users/" + fixture.targetId + "/avatar");
  check("another user without a photo returns 404 rather than the operator photo", () => assert.equal(r.status, 404));
  const records = await (await request("/api/management/users")).json();
  check("listing retains the photo flag for both kinds of users", () => {
    assert.equal(records.content.find(u => u.id === fixture.actorId).hasProfileImage, true);
    assert.equal(records.content.find(u => u.id === fixture.targetId).hasProfileImage, false);
  });
  const detail = await (await request("/api/management/users/" + fixture.actorId)).json();
  check("worker detail retains the photo flag", () => assert.equal(detail.hasProfileImage, true));
  const photoPage = await page();
  check("topbar and profile render the same authenticated URL", () => {
    const images = [...photoPage.matchAll(/<img[^>]+src="(\/api\/account\/avatar[^"]+)"/g)].map(match => match[1]);
    assert.equal(images.length, 2); assert.equal(images[0], images[1]);
  });
  r = await upload(png, "image/png", "foto.png");
  check("PNG replacement succeeds", () => assert.equal(r.status, 200));
  r = await request(endpoint + "?v=replacement");
  const pngBytes = Buffer.from(await r.arrayBuffer());
  check("replacement returns fresh PNG bytes, not the JPEG cache", () => {
    assert.equal(r.headers.get("content-type"), "image/png"); assert.match(r.headers.get("cache-control"), /no-store/); assert.deepEqual(pngBytes, png);
  });
  const me = await getMe();
  check("me reports photo flag without DNI or storage key", () => { assert.equal(me.user.hasProfileImage,true); assert.equal("dni" in me.user,false); assert.equal("profileImageKey" in me.user,false); });
  r = await upload(png, "image/webp", "fake.webp");
  check("rejects WebP", () => assert.equal(r.status, 400));
  r = await upload(png, "image/jpeg", "fake.jpg");
  check("rejects mismatched signatures", () => assert.equal(r.status, 400));
  r = await upload(Buffer.alloc(2*1024*1024+1), "image/png", "grande.png");
  check("rejects file larger than 2 MB", () => assert.equal(r.status, 413));
  r = await upload(png, "image/png", "foto.png", { origin: "https://untrusted.example" });
  check("origin check protects multipart", () => assert.equal(r.status, 403));
  r = await request(endpoint, { method:"DELETE", headers: { origin: "https://untrusted.example" } });
  check("origin check protects DELETE", () => assert.equal(r.status, 403));
  r = await upload(png, "image/png", "foto.png", { cookie: "__Host-kiments-access=expired" });
  check("expired backend session returns 401", () => { assert.equal(r.status, 401); assert.equal(fixture.avatarWrites, 2); });
  fixture.setAvatarStatus(500);
  r = await request();
  const problem = await r.json();
  check("backend 500 becomes a sanitized error without false success", () => { assert.equal(r.status, 502); assert.doesNotMatch(problem.error, /interno de prueba/); });
  fixture.setAvatarStatus(413);
  r = await upload(png, "image/png", "foto.png");
  check("upstream size error remains 413", () => assert.equal(r.status, 413));
  fixture.setAvatarStatus(0);
  r = await request(endpoint, { method: "DELETE" });
  check("delete is confirmed with 204 without clearing session", () => { assert.equal(r.status, 204); assert.equal(r.headers.get("set-cookie"), null); });
  const afterDelete = await getMe();
  r = await request();
  check("deleted avatar is 404 and me flag becomes false", () => { assert.equal(r.status, 404); assert.equal(afterDelete.user.hasProfileImage, false); });
  const updatedPage = await page();
  check("both avatar surfaces return to initials", () => { assert.doesNotMatch(updatedPage, /src="\/api\/account\/avatar/); assert.match(updatedPage, /class="ka-avatar"[^>]*><span>PA/); });
  r = await request("/api/account/password", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ currentPassword: "incorrecta", newPassword: "prueba-aislada-2026" }) });
  check("password failure preserves local session", () => { assert.equal(r.status, 401); assert.equal(r.headers.get("set-cookie"), null); });
  r = await request("/api/account/password", { method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ currentPassword: "fixture-password", newPassword: "prueba-aislada-2026" }) });
  check("confirmed password change clears the local cookies", () => { assert.equal(r.status, 204); assert.equal(r.headers.getSetCookie().length, 2); });
  console.log(`${passed} comprobaciones HTTP de Mi cuenta aprobadas (backend en memoria).`);
} finally { fixture.stop(); }
