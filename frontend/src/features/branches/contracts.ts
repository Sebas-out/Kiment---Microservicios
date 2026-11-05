import { z } from "zod";

// Verified against BranchDtos / BranchController / BranchServiceImpl, Flyway V5.
export const branchTypeSchema = z.enum(["VENTA", "ALMACEN", "MIXTO"], { error: "Selecciona el tipo de sucursal" });
export type BranchType = z.infer<typeof branchTypeSchema>;
export const branchTypeLabels: Record<BranchType, string> = { VENTA: "Venta", ALMACEN: "Almacén", MIXTO: "Mixto" };
export const branchSchema = z.object({ id: z.uuid(), code: z.string(), name: z.string(), address: z.string().nullable(), phone: z.string().nullable(), city: z.string().nullable(), ubigeo: z.string().nullable(), email: z.string().nullable(), branchType: branchTypeSchema.nullable(), sunatCode: z.string().nullable(), enabled: z.boolean(), createdAt: z.string(), updatedAt: z.string() });
export const branchPageSchema = z.object({ content: z.array(branchSchema), page: z.number().int().nonnegative(), size: z.number().int().positive(), totalElements: z.number().int().nonnegative(), totalPages: z.number().int().nonnegative() });
const fields = {
  name: z.string().trim().min(1, "Ingresa el nombre").max(100, "Máximo 100 caracteres"),
  address: z.string().trim().max(200, "Máximo 200 caracteres"),
  phone: z.string().trim().regex(/^$|^\+?[0-9 ()-]{6,20}$/, "Ingresa un teléfono válido (6 a 20 caracteres)"),
  city: z.string().trim().max(100, "Máximo 100 caracteres"),
  ubigeo: z.string().trim().regex(/^$|^[0-9]{6}$/, "El ubigeo debe tener exactamente 6 dígitos"),
  email: z.string().trim().max(150, "Máximo 150 caracteres").regex(/^$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/, "Ingresa un correo válido"),
  sunatCode: z.string().trim().regex(/^$|^[0-9]{4}$/, "El código SUNAT debe tener exactamente 4 dígitos"),
};
const codeSchema = z.string().regex(/^[A-Za-z0-9_-]{2,20}$/, "Usa de 2 a 20 letras, números, guion o guion bajo");
export const branchCreateSchema = z.object({ name: fields.name, code: codeSchema, address: fields.address.nullable().optional(), phone: fields.phone.nullable().optional(), city: fields.city.nullable().optional(), ubigeo: fields.ubigeo.nullable().optional(), email: fields.email.nullable().optional(), branchType: branchTypeSchema, sunatCode: fields.sunatCode.nullable().optional(), enabled: z.boolean() }).strict();
// null/omitted retain the current value; an empty optional string clears it.
export const branchEditSchema = z.object({ name: fields.name.nullable().optional(), address: fields.address.nullable().optional(), phone: fields.phone.nullable().optional(), city: fields.city.nullable().optional(), ubigeo: fields.ubigeo.nullable().optional(), email: fields.email.nullable().optional(), branchType: branchTypeSchema.nullable().optional(), sunatCode: fields.sunatCode.nullable().optional() }).strict();
export const branchFormSchema = z.object({ ...fields, code: z.string(), enabled: z.boolean(), branchType: z.union([branchTypeSchema,z.literal("")]) });
export type BranchFormValues = z.infer<typeof branchFormSchema>;
export function branchDefaults(branch?: Branch): BranchFormValues {
  return { name: branch?.name ?? "", code: branch?.code ?? "", address: branch?.address ?? "", phone: branch?.phone ?? "", city: branch?.city ?? "", ubigeo: branch?.ubigeo ?? "", email: branch?.email ?? "", branchType: branch?.branchType ?? "", sunatCode: branch?.sunatCode ?? "", enabled: branch?.enabled ?? true };
}
export function buildBranchPatch(values: BranchFormValues, branch: Branch) {
  const patch: Record<string, unknown> = {};
  for (const key of ["name", "address", "phone", "city", "ubigeo", "email", "sunatCode"] as const) {
    const value = values[key].trim();
    if (value !== (branch[key] ?? "")) patch[key] = value;
  }
  if (values.branchType && values.branchType !== branch.branchType) patch.branchType = values.branchType;
  return branchEditSchema.parse(patch);
}
export const branchStatusSchema = z.object({ enabled: z.boolean() }).strict();
export const branchQuerySchema = z.object({ name: z.string().max(100).optional(), code: z.string().regex(/^[A-Z0-9_-]{0,20}$/).optional(), type: branchTypeSchema.optional(), city: z.string().max(100).optional(), sunatCode: z.string().regex(/^[0-9]{0,4}$/).optional(), enabled: z.enum(["true", "false"]).optional(), page: z.coerce.number().int().min(0).max(21474836).default(0), size: z.coerce.number().int().min(1).max(100).default(20) }).strict();
export type Branch = z.infer<typeof branchSchema>;
export type BranchPage = z.infer<typeof branchPageSchema>;

// The selector must follow every server page; never silently truncate options.
export async function collectBranches(load: (page: number) => Promise<BranchPage>): Promise<Branch[]> {
  const items = new Map<string, Branch>();
  for (let page = 0; ; page++) {
    const result = branchPageSchema.parse(await load(page));
    if (result.page !== page || (result.totalPages > page + 1 && !result.content.length)) throw new Error("No se pudo completar la lista de sucursales. Intenta nuevamente.");
    for (const branch of result.content) items.set(branch.id, branch);
    if (page + 1 >= result.totalPages) {
      if (items.size < result.totalElements) throw new Error("La lista de sucursales cambió durante la consulta. Vuelve a cargarla.");
      break;
    }
  }
  return [...items.values()];
}
