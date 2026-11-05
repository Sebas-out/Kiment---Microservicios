import { z } from "zod";
import { createSchema, editSchema, changeRoleSchema, changeBranchSchema, changeStatusSchema, querySchema, recordSchema, pageSchema, rolesSchema, permissionsSchema } from "./contracts";
import { branchSchema, branchPageSchema, branchCreateSchema, branchEditSchema, branchStatusSchema, branchQuerySchema } from "../branches/contracts";

// Explicit allowlist: this is not an arbitrary upstream proxy.
export function operation(method: string, parts: string[], search: URLSearchParams) {
  const [resource, id, action] = parts;
  if (resource === "branches") {
    if (parts.length === 1 && method === "GET") {
      const query = branchQuerySchema.parse(Object.fromEntries(search));
      return { permission: "BRANCH_READ", path: `branches?${new URLSearchParams(Object.entries(query).map(([k,v]) => [k,String(v)]))}`, body: null, response: branchPageSchema };
    }
    if (parts.length === 1 && method === "POST") return { permission: "BRANCH_CREATE", path: "branches", body: branchCreateSchema, response: branchSchema };
    if (z.uuid().safeParse(id).success) {
      if (parts.length === 2 && method === "GET") return { permission: "BRANCH_READ", path: `branches/${id}`, body: null, response: branchSchema };
      if (parts.length === 2 && method === "PATCH") return { permission: "BRANCH_UPDATE", path: `branches/${id}`, body: branchEditSchema, response: branchSchema };
      if (parts.length === 3 && action === "status" && method === "PATCH") return { permission: "BRANCH_DISABLE", path: `branches/${id}/status`, body: branchStatusSchema, response: branchSchema };
    }
    return null;
  }
  if (parts.length === 1 && resource === "users" && method === "GET") {
    const query = querySchema.parse(Object.fromEntries(search));
    return { permission: "USER_READ", path: `users?${new URLSearchParams(Object.entries(query).map(([k,v]) => [k, String(v)]))}`, body: null, response: pageSchema };
  }
  if (parts.length === 1 && resource === "users" && method === "POST") return { permission: "USER_CREATE", path: "users", body: createSchema, response: recordSchema };
  if (resource === "users" && z.uuid().safeParse(id).success) {
    if (parts.length === 2 && method === "GET") return { permission: "USER_READ", path: `users/${id}`, body: null, response: recordSchema };
    if (parts.length === 2 && method === "PATCH") return { permission: "USER_UPDATE", path: `users/${id}`, body: editSchema, response: recordSchema };
    if (parts.length === 3 && method === "PATCH" && action === "role") return { permission: "USER_ROLE_CHANGE", path: `users/${id}/role`, body: changeRoleSchema, response: recordSchema };
    if (parts.length === 3 && method === "PATCH" && action === "branch") return { permission: "USER_UPDATE", path: `users/${id}/branch`, body: changeBranchSchema, response: recordSchema };
    if (parts.length === 3 && method === "PATCH" && action === "status") return { permission: "USER_DISABLE", path: `users/${id}/status`, body: changeStatusSchema, response: recordSchema };
  }
  if (resource === "roles" && method === "GET") {
    if (parts.length === 1) return { permission: "USER_READ", path: "roles", body: null, response: rolesSchema };
    if (parts.length === 3 && /^[1-9][0-9]{0,14}$/.test(id) && action === "permissions") return { permission: "USER_READ", path: `roles/${id}/permissions`, body: null, response: permissionsSchema };
  }
  return null;
}

export function permitted(role: string, enabled: boolean, permissions: string[], required: string) {
  return enabled && role === "ADMIN" && permissions.includes(required);
}

export function backendProblem(status: number, value: unknown): { error: string; fields?: Record<string,string> } {
  const messages: Record<number,string> = { 400: "Revisa los datos de la solicitud.", 401: "Tu sesión ha expirado. Inicia sesión nuevamente.", 403: "No tienes permiso para realizar esta operación.", 404: "El recurso ya no existe. Actualiza la lista.", 409: "Los datos están duplicados o cambiaron. Actualiza e inténtalo nuevamente.", 429: "Demasiadas solicitudes. Espera un momento." };
  let error = messages[status] ?? "No se pudo completar la operación. Intenta nuevamente.";
  // Only known business messages; never echo arbitrary upstream payloads or values.
  const problem = z.object({ detail: z.string().optional(), errors: z.array(z.object({ field: z.string(), message: z.string() })).optional() }).safeParse(value);
  const allowed = ["El correo ya está registrado", "El DNI ya está registrado", "No puede cambiar su propio rol", "Debe existir al menos un administrador activo", "El código de sucursal ya está registrado", "El código de establecimiento SUNAT ya está registrado", "Los administradores tienen alcance global y no deben tener una sucursal asignada", "Los trabajadores operativos deben tener una sucursal asignada", "No se puede asignar una sucursal inactiva", "Debe especificar una sucursal para asignar al usuario un rol operativo", "El usuario no tiene una sucursal asignada; debe especificar una sucursal válida", "La sucursal actual del usuario está inactiva o no existe; debe especificar una sucursal activa", "No puede cambiar su propia sucursal", "Los administradores tienen alcance global y no pueden ser asignados a una sucursal"];
  if (status === 409 && problem.success && allowed.includes(problem.data.detail ?? "")) error = problem.data.detail!;
  if (status === 409 && problem.success && /^No se puede desactivar la sucursal porque tiene [1-9][0-9]* trabajador(?: activo asignado|es activos asignados)\. Reasígnelos o desactívelos primero\.$/.test(problem.data.detail ?? "")) error = problem.data.detail!;
  const fields: Record<string,string> = {};
  if (status === 400 && problem.success) for (const item of problem.data.errors ?? []) {
    if (["firstName","lastName","dni","phone","email","password","role","enabled","branchId","code","name","address","city","ubigeo","email","branchType","sunatCode"].includes(item.field)) fields[item.field] = "Revisa este campo según los requisitos indicados.";
  }
  return { error, ...(Object.keys(fields).length ? { fields } : {}) };
}
