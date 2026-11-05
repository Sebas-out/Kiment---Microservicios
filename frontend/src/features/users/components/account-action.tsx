"use client";
import { useRef, useState } from "react";
import { type Role } from "@/features/auth/contracts";
import { notify } from "@/lib/notifications";
import { usersRequest } from "../client";
import type { RoleRecord, UserRecord } from "../contracts";
import { UserDialog } from "./dialog";
import { RoleSelector } from "./role-selector";
import { BranchPicker } from "./branch-picker";

export function AccountAction({ user, kind, roles, permissions, onClose, onSaved }: { user: UserRecord; kind: "role" | "status" | "branch"; roles: RoleRecord[]; permissions: string[]; onClose: () => void; onSaved: (user: UserRecord) => void }) {
  const [role, setRole] = useState<Role>(user.role);
  const [branchId, setBranchId] = useState(user.branchId ?? "");
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const sending = useRef(false);
  const [error, setError] = useState("");
  const losingAdmin = kind === "role" && user.role === "ADMIN" && role !== "ADMIN";
  const title = kind === "role" ? "Cambiar rol" : kind === "branch" ? "Reasignar sucursal" : user.enabled ? "Desactivar usuario" : "Reactivar usuario";
  async function save() {
    if (sending.current || (losingAdmin && !confirmed) || (kind === "role" && role !== "ADMIN" && !branchId) || (kind === "branch" && !branchId)) return;
    sending.current = true; setBusy(true); setError("");
    try {
      const saved = await usersRequest<UserRecord>(`users/${user.id}/${kind}`, "PATCH", kind === "role" ? { role, branchId: role === "ADMIN" ? null : branchId } : kind === "branch" ? { branchId } : { enabled: !user.enabled });
      notify.success(kind === "role" ? "Rol actualizado correctamente" : kind === "branch" ? "Sucursal reasignada correctamente" : user.enabled ? "Usuario desactivado correctamente" : "Usuario reactivado correctamente");
      onSaved(saved);
    } catch (err) { const message = err instanceof Error ? err.message : "No se pudo completar la operación."; setError(message); notify.error(message); }
    finally { sending.current = false; setBusy(false); }
  }
  return <UserDialog title={title} className={kind === "role" ? "ku-wide-dialog" : ""} busy={busy} onClose={onClose}>
    <p className="ku-muted">{user.firstName} {user.lastName} · {user.email}</p>
    <form onSubmit={e => { e.preventDefault(); void save(); }} aria-busy={busy}>
      {kind === "role" ? <><RoleSelector roles={roles} value={role} disabled={busy} onChange={code => { setRole(code); setConfirmed(false); if (code === "ADMIN") setBranchId(""); }} /><BranchPicker global={role === "ADMIN"} value={branchId} onChange={setBranchId} permissions={permissions} disabled={busy} />
        {losingAdmin && <label className="ku-check ku-warning"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e => setConfirmed(e.target.checked)} /> Confirmo que esta cuenta perderá sus permisos administrativos.</label>}
        <p className="ku-muted">El cambio de rol revoca las sesiones de renovación de esta cuenta.</p></> : kind === "branch" ? <><p>Selecciona la nueva sucursal de este trabajador. La asignación se actualizará al confirmar.</p><BranchPicker value={branchId} onChange={setBranchId} permissions={permissions} disabled={busy} /></> : <p>{user.enabled ? "Esta cuenta dejará de tener acceso al sistema y se revocarán sus sesiones de renovación. ¿Deseas desactivarla?" : "Esta cuenta volverá a tener acceso al sistema. ¿Deseas reactivarla?"}</p>}
      {error && <p role="alert" className="ku-error">{error}</p>}
      <div className="ku-dialog-actions"><button type="button" className="ku-button" onClick={onClose} disabled={busy}>Cancelar</button><button className={`ku-button ${kind === "status" && user.enabled ? "ku-danger" : "ku-primary"}`} disabled={busy || (kind === "role" && ((role === user.role && branchId === (user.branchId ?? "")) || !roles.length || (role !== "ADMIN" && !branchId) || (losingAdmin && !confirmed))) || (kind === "branch" && (!branchId || branchId === user.branchId))}>{busy ? "Procesando…" : title}</button></div>
    </form>
  </UserDialog>;
}
