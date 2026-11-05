"use client";
import { useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { notify } from "@/lib/notifications";
import { createSchema, editSchema, type RoleRecord, type UserRecord } from "../contracts";
import { usersRequest, UsersError } from "../client";
import { UserDialog } from "./dialog";
import { RoleSelector } from "./role-selector";
import { BranchPicker } from "./branch-picker";

// Use raw form values; only the API schema transforms the optional creation phone to null.
const formSchema = editSchema.extend({ password: z.string(), role: z.string(), enabled: z.boolean(), branchId: z.string() });
type Values = z.infer<typeof formSchema>;
export function UserForm({ user, roles, permissions, onClose, onSaved }: { user?: UserRecord; roles: RoleRecord[]; permissions: string[]; onClose: () => void; onSaved: (user: UserRecord) => void }) {
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  const [error, setError] = useState("");
  const { register, handleSubmit, setValue, setError: fieldError, reset, control, formState: { errors } } = useForm<Values>({ resolver: zodResolver(formSchema.superRefine((values, context) => {
    if (user) return;
    const result = createSchema.safeParse({ ...values, branchId: values.role === "ADMIN" ? null : values.branchId || null });
    if (!result.success) for (const issue of result.error.issues) {
      if (["password","role","branchId"].includes(String(issue.path[0]))) context.addIssue({ code: "custom", path: issue.path, message: issue.path[0] === "role" ? "Selecciona un rol válido" : issue.message });
    }
  })), defaultValues: { firstName: user?.firstName ?? "", lastName: user?.lastName ?? "", dni: user?.dni ?? "", phone: user?.phone ?? "", email: user?.email ?? "", password: "", role: user?.role ?? "ADMIN", enabled: user?.enabled ?? true, branchId: user?.branchId ?? "" } });
  const role = useWatch({ control, name: "role" });
  const branchId = useWatch({ control, name: "branchId" });
  const close = () => { reset(); onClose(); };
  async function save(values: Values) {
    if (sending.current) return;
    setError("");
    const fields = { firstName: values.firstName, lastName: values.lastName, dni: values.dni, phone: values.phone, email: values.email };
    const parsed = user ? editSchema.safeParse(fields) : createSchema.safeParse({ ...fields, password: values.password, role: values.role, enabled: values.enabled, branchId: values.role === "ADMIN" ? null : values.branchId || null });
    if (!parsed.success) { for (const issue of parsed.error.issues) fieldError(issue.path[0] as keyof Values, { message: issue.message }); return; }
    sending.current = true; setBusy(true);
    try {
      const saved = await usersRequest<UserRecord>(user ? `users/${user.id}` : "users", user ? "PATCH" : "POST", parsed.data);
      reset(); notify.success(user ? "Usuario actualizado correctamente" : "Usuario creado correctamente"); onSaved(saved);
    } catch (err) {
      const message = err instanceof Error ? err.message : "No se pudo guardar el usuario.";
      setError(message); notify.error(message);
      if (err instanceof UsersError) for (const [key, message] of Object.entries(err.fields)) if (key in values) fieldError(key as keyof Values, { message });
    } finally { sending.current = false; setBusy(false); }
  }
  const field = (key: "firstName" | "lastName" | "dni" | "phone" | "email" | "password", label: string, type = "text", maxLength = 80) => <label className={key === "email" || key === "password" ? "ku-span" : ""} key={key} htmlFor={`ku-${key}`}>{label}
    <input id={`ku-${key}`} type={type} {...register(key)} disabled={busy} maxLength={maxLength} autoComplete={key === "password" ? "new-password" : "off"} inputMode={key === "dni" ? "numeric" : undefined} aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `ku-${key}-error` : key === "password" ? "ku-password-help" : undefined} />
    {key === "password" && <small id="ku-password-help">Entre 12 y 128 caracteres.</small>}
    {errors[key] && <small id={`ku-${key}-error`} className="ku-field-error" role="alert">{errors[key]?.message}</small>}
  </label>;
  return <UserDialog title={user ? "Editar Usuario" : "Nuevo Usuario"} className="ku-wide-dialog" busy={busy} onClose={close}>
    <p className="ku-muted">{user ? "Actualiza los datos personales de esta cuenta." : "Completa los datos para registrar una cuenta de personal."}</p>
    <form noValidate onSubmit={event => { void handleSubmit(save)(event); }} aria-busy={busy}>
      <div className="ku-form-grid">{field("firstName", "Nombre")}{field("lastName", "Apellido")}{field("dni", "DNI", "text", 8)}{field("phone", "Teléfono (opcional)", "tel", 20)}{field("email", "Correo electrónico", "email", 254)}{!user && field("password", "Contraseña inicial", "password", 128)}
      </div>
      {!user && <><div className="ku-role-label"><span>Rol y permisos</span><label className="ku-check">Activo <input type="checkbox" role="switch" {...register("enabled")} disabled={busy} /></label></div><RoleSelector roles={roles} value={role} disabled={busy} onChange={code => { setValue("role", code, { shouldValidate: true }); if (code === "ADMIN") setValue("branchId", ""); }} /><BranchPicker global={role === "ADMIN"} value={branchId} onChange={id => setValue("branchId", id, { shouldValidate: true })} permissions={permissions} disabled={busy} error={errors.branchId?.message} /></>}
      {error && <p className="ku-error" role="alert">{error}</p>}
      <div className="ku-dialog-actions"><button type="button" className="ku-button" onClick={close} disabled={busy}>Cancelar</button><button className="ku-button ku-primary" disabled={busy || (!user && (!roles.length || (role !== "ADMIN" && !branchId)))}>{busy ? "Guardando…" : user ? "Guardar cambios" : "Crear usuario"}</button></div>
    </form>
  </UserDialog>;
}
