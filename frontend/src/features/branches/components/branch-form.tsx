"use client";
import { useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { UserDialog } from "@/features/users/components/dialog";
import { usersRequest, UsersError } from "@/features/users/client";
import { notify } from "@/lib/notifications";
import { branchCreateSchema, branchFormSchema, branchDefaults, buildBranchPatch, type BranchFormValues, type Branch } from "../contracts";

type Values = BranchFormValues;
export function BranchForm({ branch, onClose, onSaved }: { branch?: Branch; onClose: () => void; onSaved: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(false);
  const { register, reset, control, setError: fieldError, handleSubmit, formState: { errors } } = useForm<Values>({ resolver: zodResolver(branchFormSchema.superRefine((values, ctx) => {
    if (!branch) { const result = branchCreateSchema.safeParse(values); if (!result.success) for (const issue of result.error.issues) if (issue.path[0] === "code" || issue.path[0] === "branchType") ctx.addIssue({ code: "custom", path: issue.path, message: issue.message }); }
  })), defaultValues: branchDefaults(branch) });
  const enabled = useWatch({ control, name: "enabled" });
  const close = () => { reset(); onClose(); };
  async function save(values: Values) {
    if (pending.current) return;
    const parsed = branch ? ({ success: true as const, data: buildBranchPatch(values, branch) }) : branchCreateSchema.safeParse(values);
    if (!parsed.success) { for (const issue of parsed.error.issues) fieldError(issue.path[0] as keyof Values, { message: issue.message }); return; }
    if (branch && !Object.keys(parsed.data).length) { notify.info("No hay cambios para guardar."); return; }
    pending.current = true; setBusy(true); setError("");
    try { await usersRequest(branch ? `branches/${branch.id}` : "branches", branch ? "PATCH" : "POST", parsed.data); notify.success(branch ? "Sucursal actualizada correctamente" : "Sucursal creada correctamente"); reset(); onSaved(); }
    catch (err) { const message = err instanceof Error ? err.message : "No se pudo guardar la sucursal."; setError(message); notify.error(message); if (err instanceof UsersError) for (const [key,message] of Object.entries(err.fields)) if (key in values) fieldError(key as keyof Values, { message }); }
    finally { pending.current = false; setBusy(false); }
  }
  function field(key: "code" | "name" | "address" | "phone" | "city" | "ubigeo" | "email" | "sunatCode", label: string, max: number, full = false) {
    return <label className={full ? "ku-span" : ""} htmlFor={`kb-${key}`}>{label}<input id={`kb-${key}`} {...register(key)} readOnly={key === "code" && !!branch} disabled={busy} maxLength={max} aria-invalid={!!errors[key]} aria-describedby={[errors[key] ? `kb-${key}-error` : "", key === "sunatCode" ? "kb-sunat-help" : ""].filter(Boolean).join(" ") || undefined} autoComplete="off" type={key === "email" ? "email" : "text"} inputMode={key === "ubigeo" || key === "sunatCode" ? "numeric" : undefined} />{key === "sunatCode" && <small id="kb-sunat-help">Cuatro dígitos del establecimiento SUNAT; distinto del código interno.</small>}{errors[key] && <small id={`kb-${key}-error`} className="ku-field-error" role="alert">{errors[key]?.message}</small>}</label>;
  }
  return <UserDialog title={branch ? "Editar Sucursal" : "Nueva Sucursal"} className="kb-dialog" busy={busy} onClose={close}>
    <p className="ku-muted">{branch ? "Modifica los datos de la sucursal seleccionada." : "Completa los datos para registrar una nueva sucursal."}</p>
    <form noValidate onSubmit={event => { void handleSubmit(save)(event); }} aria-busy={busy}>
      <div className="ku-form-grid">
        {field("name", "Nombre", 100, true)}{field("code", "Código interno", 20, true)}
        {branch && <small className="ku-span ku-muted">El código interno no puede modificarse.</small>}
        {field("city", "Ciudad (opcional)", 100)}{field("address", "Dirección (opcional)", 200)}
        {field("phone", "Teléfono (opcional)", 20)}{field("email", "Correo electrónico (opcional)", 150)}
        {field("ubigeo", "Ubigeo (opcional)", 6)}{field("sunatCode", "Cód. establecimiento SUNAT (opcional)", 4)}
        <label htmlFor="kb-branchType">Tipo de sucursal{!branch && " *"}<select id="kb-branchType" {...register("branchType")} disabled={busy} aria-required={!branch} aria-invalid={!!errors.branchType} aria-describedby={errors.branchType ? "kb-branchType-error" : undefined}><option value="" disabled={!!branch?.branchType}>{branch ? "Sin clasificar" : "Selecciona un tipo"}</option><option value="VENTA">Venta — Tienda / local comercial</option><option value="ALMACEN">Almacén — Centro de almacenamiento</option><option value="MIXTO">Mixto — Tienda y almacén</option></select>{errors.branchType && <small id="kb-branchType-error" role="alert" className="ku-field-error">{errors.branchType.message}</small>}</label>
        <label className="kb-company">Empresa asociada<input disabled placeholder="Pendiente de Empresa / Facturación" /><small>Disponible cuando se implemente Empresa.</small></label>
        <label className="ku-span">Estado<span className="kb-state-field"><span>{enabled ? "Activo" : "Inactivo"}</span>{branch ? <small>Se cambia desde la tarjeta de la sucursal.</small> : <input type="checkbox" role="switch" aria-label="Sucursal activa" {...register("enabled")} disabled={busy} />}</span></label>
      </div>
      {error && <p className="ku-error" role="alert">{error}</p>}
      <div className="ku-dialog-actions"><button type="button" className="ku-button" disabled={busy} onClick={close}>Cancelar</button><button className="ku-button ku-primary" disabled={busy}>{busy ? "Guardando…" : branch ? "Guardar cambios" : "Guardar"}</button></div>
    </form>
  </UserDialog>;
}
