"use client";
import { useEffect, useState } from "react";
import { CircleUserRound, X, Mail, Phone, ContactRound, Building2, CalendarDays } from "lucide-react";
import { roleLabels } from "@/features/auth/contracts";
import { usersRequest } from "../client";
import type { RoleRecord, UserRecord } from "../contracts";
import { RolePermissions } from "./role-permissions";
import type { Branch } from "@/features/branches/contracts";
import { WorkerAvatar } from "./worker-avatar";

export function dateLabel(value: string | null) {
  if (!value) return "Sin registro";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "Sin registro" : new Intl.DateTimeFormat("es-PE", { dateStyle: "medium", timeStyle: "short", timeZone: "America/Lima" }).format(date);
}
export function UserDetail({ id, revision, roles, branches, permissions, actorId, onAction, onClose }: { id?: string; revision: number; roles: RoleRecord[]; branches?: Branch[]; permissions: string[]; actorId: string; onAction: (kind: "edit" | "role" | "status" | "branch", user: UserRecord) => void; onClose: () => void }) {
  const [state, setState] = useState<{ key: string; data?: UserRecord; error?: string }>();
  const [retry, setRetry] = useState(0);
  const key = `${id}:${revision}:${retry}`;
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    usersRequest<UserRecord>(`users/${id}`, "GET", undefined, controller.signal).then(data => setState({ key, data })).catch(err => { if (!controller.signal.aborted) setState({ key, error: err.message }); });
    return () => controller.abort();
  }, [id, key]);
  if (!id) return <aside className="admin-card ku-detail ku-detail-empty"><CircleUserRound size={34} strokeWidth={1.3} /><h3>Ningún usuario seleccionado</h3><p>Selecciona una cuenta para consultar sus datos y permisos.</p></aside>;
  const current = state?.key === key ? state : undefined;
  const user = current?.data;
  return <aside className="admin-card ku-detail" aria-label="Detalle del usuario" aria-busy={!current}><button className="ku-icon ku-detail-close" aria-label="Cerrar detalle del usuario" onClick={onClose}><X size={16} /></button><h3 className="sr-only">Detalle del usuario</h3>
    {!current && <p role="status">Cargando detalle…</p>}
    {current?.error && <p role="alert" className="ku-error">{current.error} <button className="ku-link" onClick={() => setRetry(v => v + 1)}>Reintentar</button></p>}
    {user && <><header className="ku-detail-profile"><WorkerAvatar user={user} /><h4>{user.firstName} {user.lastName}</h4><div><span className="ku-role" data-role={user.role}>{roleLabels[user.role]}</span><span className="ku-enabled" data-enabled={user.enabled}>{user.enabled ? "Activo" : "Inactivo"}</span></div></header><div className="ku-detail-info"><h5>Información del usuario</h5><dl>{[
      { label: "DNI", value: user.dni || "Sin registro", Icon: ContactRound },
      { label: "Teléfono", value: user.phone || "Sin registro", Icon: Phone },
      { label: "Correo", value: user.email, Icon: Mail },
      { label: "Sucursal", value: user.role === "ADMIN" ? "Global · Acceso a todas las sucursales" : branches?.find(b => b.id === user.branchId)?.name ?? (user.branchId ? "Asignada (detalle no disponible)" : "Pendiente de asignación"), Icon: Building2 },
      { label: "Fecha de registro", value: dateLabel(user.createdAt), Icon: CalendarDays },
    ].map(({label,value,Icon}) => <div key={label}><Icon size={15} /><div><dt>{label}</dt><dd>{value}</dd></div></div>)}</dl>
      <RolePermissions id={roles.find(r => r.code === user.role)?.id} />
      </div><div className="ku-detail-actions">{permissions.includes("USER_UPDATE") && <button className="ku-button" onClick={() => onAction("edit",user)}>Editar información</button>}{permissions.includes("USER_ROLE_CHANGE") && user.id !== actorId && <button className="ku-button" onClick={() => onAction("role",user)}>Cambiar rol</button>}{permissions.includes("USER_UPDATE") && user.role !== "ADMIN" && user.id !== actorId && <button className="ku-button" onClick={() => onAction("branch",user)}>Reasignar sucursal</button>}{permissions.includes("USER_DISABLE") && <button className="ku-button" onClick={() => onAction("status",user)}>{user.enabled ? "Desactivar usuario" : "Reactivar usuario"}</button>}</div>
    </>}
  </aside>;
}
