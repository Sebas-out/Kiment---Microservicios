import { test } from "node:test";
import assert from "node:assert/strict";
import { accountProfileSchema, profileSchema } from "../src/features/auth/contracts.ts";
import { avatarFileProblem, avatarSource, matchesAvatarSignature, maxAvatarBytes } from "../src/features/account/avatar-contract.ts";

test("validates allowed extensions, mime, empty and size limits", () => {
  const png = { name: "foto.PNG", type: "image/png", size: maxAvatarBytes };
  assert.equal(avatarFileProblem(png), undefined);
  assert.equal(avatarFileProblem({ name: "foto.JPEG", type: "image/jpeg", size: 1024 }), undefined);
  assert.equal(avatarFileProblem({ ...png, size: maxAvatarBytes + 1 })?.status, 413);
  assert.equal(avatarFileProblem({ ...png, size: 0 })?.status, 400);
  assert.equal(avatarFileProblem({ ...png, name: "foto.jpg" })?.status, 400);
  assert.equal(avatarFileProblem({ ...png, type: "image/webp", name: "foto.webp" })?.status, 400);
});

test("requires binary signatures matching JPEG or PNG", () => {
  assert.equal(matchesAvatarSignature("image/jpeg", Uint8Array.of(255,216,255)), true);
  const png = Uint8Array.of(137,80,78,71,13,10,26,10);
  assert.equal(matchesAvatarSignature("image/png", png), true);
  assert.equal(matchesAvatarSignature("image/jpeg", png), false);
  assert.equal(matchesAvatarSignature("image/png", png.slice(0,4)), false);
});

test("only constructs a same-origin versioned URL when the session has a photo", () => {
  assert.equal(avatarSource(false, "ignored"), null);
  assert.equal(avatarSource(true, "?x=y"), "/api/account/avatar?v=%3Fx%3Dy");
  assert.notEqual(avatarSource(true, "before"), avatarSource(true, "after"));
});

test("the server profile keeps own account data without extending the public session shape", () => {
  const user = { id: "321c9f1d-624d-4fe5-8c70-9b43396059b2", firstName: "Prueba", lastName: "Aislada",
    role: "ADMIN", enabled: true, email: "qa@example.invalid", branchId: null, dni: "01234567",
    phone: null, createdAt: "2026-11-04T17:00:00Z", hasProfileImage: true, profileImageKey: "secret.jpg" };
  const data = { user, permissions: [] };
  const account = accountProfileSchema.parse(data);
  assert.equal(account.user.dni, user.dni);
  assert.equal(account.user.createdAt, user.createdAt);
  assert.equal(account.user.hasProfileImage, true);
  assert.equal("profileImageKey" in account.user, false);
  const publicSession = profileSchema.parse(account);
  assert.equal(publicSession.user.hasProfileImage, true);
  assert.equal("dni" in publicSession.user, false);
  assert.equal(accountProfileSchema.safeParse({ ...data, user: { ...user, hasProfileImage: undefined } }).success, true);
});
