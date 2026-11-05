import { z } from "zod";
import { roleSchema } from "../auth/contracts";

// Verified against UserDtos, UserController and RoleService in SVC-09.
export const recordSchema = z.object({
  id: z.uuid(), firstName: z.string(), lastName: z.string(), dni: z.string().nullable(),
  phone: z.string().nullable(), email: z.email(), role: roleSchema, enabled: z.boolean(),
  hasProfileImage: z.boolean().optional(),
  branchId: z.uuid().nullable(), createdAt: z.string(), updatedAt: z.string(), lastLoginAt: z.string().nullable(),
});
export const pageSchema = z.object({ content: z.array(recordSchema), page: z.number().int().nonnegative(), size: z.number().int().positive(), totalElements: z.number().int().nonnegative(), totalPages: z.number().int().nonnegative() });
export const rolesSchema = z.array(z.object({ id: z.number().int().positive(), code: roleSchema, name: z.string(), description: z.string().nullable() }));
export const permissionsSchema = z.array(z.object({ code: z.string(), description: z.string() }));
const fields = {
  firstName: z.string().trim().min(1, "Ingresa el nombre").max(80, "Máximo 80 caracteres"),
  lastName: z.string().trim().min(1, "Ingresa el apellido").max(80, "Máximo 80 caracteres"),
  dni: z.string().regex(/^[0-9]{8}$/, "El DNI debe tener 8 dígitos"),
  phone: z.string().regex(/^$|^\+?[0-9 ()-]{6,20}$/, "Ingresa un teléfono válido (6 a 20 caracteres)"),
  email: z.email("Ingresa un correo válido").max(254, "Máximo 254 caracteres"),
};
export const editSchema = z.object(fields).strict();
export const createSchema = z.object({ ...fields, phone: fields.phone.nullable().transform(v => v || null), password: z.string().min(12, "La contraseña debe tener al menos 12 caracteres").max(128, "Máximo 128 caracteres").refine(v => v.trim().length > 0, "La contraseña no puede contener solo espacios"), role: roleSchema, enabled: z.boolean(), branchId: z.uuid("Selecciona una sucursal válida").nullable().optional() }).strict().superRefine((data, ctx) => {
  if (data.role !== "ADMIN" && !data.branchId) ctx.addIssue({ code: "custom", path: ["branchId"], message: "La sucursal es obligatoria para el rol seleccionado." });
  if (data.role === "ADMIN" && data.branchId) ctx.addIssue({ code: "custom", path: ["branchId"], message: "El administrador tiene acceso global." });
});
export const changeRoleSchema = z.object({ role: roleSchema, branchId: z.uuid().nullable().optional() }).strict().superRefine((data, ctx) => {
  if (data.role === "ADMIN" && data.branchId) ctx.addIssue({ code: "custom", path: ["branchId"], message: "El administrador tiene acceso global." });
});
export const changeBranchSchema = z.object({ branchId: z.uuid("Selecciona una sucursal válida") }).strict();
export const changeStatusSchema = z.object({ enabled: z.boolean() }).strict();
export const querySchema = z.object({ name: z.string().max(160).optional(), email: z.email().max(254).optional(), dni: z.string().regex(/^[0-9]{8}$/).optional(), role: roleSchema.optional(), branchId: z.uuid().optional(), enabled: z.enum(["true", "false"]).optional(), page: z.coerce.number().int().min(0).max(21474836).default(0), size: z.coerce.number().int().min(1).max(100).default(20) }).strict();
export type UserRecord = z.infer<typeof recordSchema>;
export type UserPage = z.infer<typeof pageSchema>;
export type RoleRecord = z.infer<typeof rolesSchema>[number];
export type PermissionRecord = z.infer<typeof permissionsSchema>[number];
export type UserFields = z.input<typeof editSchema>;

export function userQuery(search: string, mode: "name" | "email" | "dni", role: string, enabled: string, page: number, branchId = "") {
  const query = new URLSearchParams({ page: String(page), size: "10" });
  if (search.trim()) query.set(mode, search.trim());
  if (role) query.set("role", role);
  if (enabled) query.set("enabled", enabled);
  if (branchId) query.set("branchId", branchId);
  return query;
}
