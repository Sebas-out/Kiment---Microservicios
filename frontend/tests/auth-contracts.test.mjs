import { test } from "node:test";
import assert from "node:assert/strict";
import { profileSchema, tokenSchema, canReadUsers } from "../src/features/auth/contracts.ts";
import { loginSchema } from "../src/features/auth/schema.ts";

// Isolated DTO fixtures. No accounts or sessions are created in the application.
const user = { id: "321c9f1d-624d-4fe5-8c70-9b43396059b2", firstName: "Nombre", lastName: "Prueba", email: "fixture@example.com", role: "ADMIN", enabled: true, branchId: null };
const profile = (role = "ADMIN", permissions = ["USER_READ"]) => ({ user: { ...user, role }, permissions });

test("parses the actual nested /me DTO and strips unused personal fields", () => {
  const result = profileSchema.parse({ ...profile(), user: { ...user, dni: "00000000", phone: null } });
  assert.equal(result.user.role, "ADMIN");
  assert.equal(result.user.branchId, null);
  assert.equal("dni" in result.user, false);
  assert.equal("phone" in result.user, false);
});
test("rejects the old provisional flat /me shape", () => {
  assert.equal(profileSchema.safeParse({ id: user.id, email: user.email, name: "Prueba", roles: ["ADMIN"] }).success, false);
});
test("parses Spring Boot tokens without inventing refreshExpiresIn", () => {
  const result = tokenSchema.parse({ accessToken: "opaque-test-value", tokenType: "Bearer", expiresIn: 900, refreshToken: "r".repeat(43) });
  assert.equal(result.expiresIn, 900);
  assert.equal("refreshExpiresIn" in result, false);
});
test("rejects wrong token type, missing expiry and invalid refresh format", () => {
  const base = { accessToken: "opaque-test-value", tokenType: "Bearer", expiresIn: 900, refreshToken: "r".repeat(43) };
  assert.equal(tokenSchema.safeParse({ ...base, tokenType: "Basic" }).success, false);
  assert.equal(tokenSchema.safeParse({ ...base, expiresIn: 0 }).success, false);
  assert.equal(tokenSchema.safeParse({ ...base, refreshToken: "invalid" }).success, false);
});
test("ADMIN requires USER_READ to access Usuarios", () => {
  assert.equal(canReadUsers(profile()), true);
  assert.equal(canReadUsers(profile("ADMIN", [])), false);
});
test("non-admin roles cannot access Usuarios even with a stray USER_READ", () => {
  for (const role of ["VENTAS", "ALMACEN", "VENTAS_ALMACEN"]) assert.equal(canReadUsers(profile(role)), false);
});
test("a disabled administrator is denied", () => {
  assert.equal(canReadUsers({ ...profile(), user: { ...user, enabled: false } }), false);
});
test("unknown roles and fabricated GLOBAL branch ids are not accepted", () => {
  assert.equal(profileSchema.safeParse(profile("SUPERADMIN")).success, false);
  assert.equal(profileSchema.safeParse({ ...profile(), user: { ...user, branchId: "GLOBAL" } }).success, false);
});
test("login respects Spring Boot's 128-character password limit", () => {
  assert.equal(loginSchema.safeParse({ email: user.email, password: "p".repeat(128) }).success, true);
  assert.equal(loginSchema.safeParse({ email: user.email, password: "p".repeat(129) }).success, false);
});
