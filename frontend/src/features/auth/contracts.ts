import { z } from "zod";

// Verified against AuthenticationDtos, UserDtos and V2__roles_and_permissions.sql.
export const roleSchema = z.enum(["ADMIN", "VENTAS", "ALMACEN", "VENTAS_ALMACEN"]);
export type Role = z.infer<typeof roleSchema>;
export const roleLabels: Record<Role, string> = {
  ADMIN: "Administrador", VENTAS: "Ventas", ALMACEN: "Almacén", VENTAS_ALMACEN: "Ventas y almacén",
};
export const userSchema = z.object({
  id: z.uuid(), firstName: z.string().min(1), lastName: z.string().min(1),
  email: z.email(), role: roleSchema, enabled: z.boolean(), branchId: z.uuid().nullable(),
  hasProfileImage: z.boolean().optional(),
});
export const profileSchema = z.object({ user: userSchema, permissions: z.array(z.string()) });
export const accountProfileSchema = profileSchema.extend({ user: userSchema.extend({
  dni: z.string().nullish(), phone: z.string().nullish(),
  createdAt: z.string().nullish(), updatedAt: z.string().nullish(),
}) });
export type AccountProfile = z.infer<typeof accountProfileSchema>;
export type AccountUser = AccountProfile["user"];
export const tokenSchema = z.object({
  accessToken: z.string().min(1).max(3500), tokenType: z.literal("Bearer"),
  expiresIn: z.number().int().positive().max(86400),
  refreshToken: z.string().regex(/^[A-Za-z0-9_-]{43}$/),
});
export type AuthProfile = z.infer<typeof profileSchema>;
export type TokenResponse = z.infer<typeof tokenSchema>;
export type SessionIdentity = Pick<AuthProfile["user"], "firstName" | "lastName" | "email" | "role">;

export function canReadUsers(profile: AuthProfile): boolean {
  return profile.user.enabled && profile.user.role === "ADMIN" && profile.permissions.includes("USER_READ");
}
