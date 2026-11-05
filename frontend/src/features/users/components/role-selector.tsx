"use client";
import { useEffect, useState } from "react";
import { ShieldCheck, ShoppingCart, Package, Boxes, Check, X } from "lucide-react";
import { roleLabels, type Role } from "@/features/auth/contracts";
import { usersRequest } from "../client";
import type { PermissionRecord, RoleRecord } from "../contracts";

export const roleIcons = { ADMIN: ShieldCheck, VENTAS: ShoppingCart, ALMACEN: Package, VENTAS_ALMACEN: Boxes };
const order: Role[] = ["ADMIN", "VENTAS", "ALMACEN", "VENTAS_ALMACEN"];
export function RoleSelector({ roles, value, onChange, disabled = false }: { roles: RoleRecord[]; value: string; onChange: (role: Role) => void; disabled?: boolean }) {
  const key = roles.map(r => r.id).join(",");
  const [state, setState] = useState<{ key: string; items?: Record<string, PermissionRecord[]>; error?: string }>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    Promise.all(roles.map(async role => [role.code, await usersRequest<PermissionRecord[]>(`roles/${role.id}/permissions`, "GET", undefined, controller.signal)] as const))
      .then(items => setState({ key, items: Object.fromEntries(items) }))
      .catch(error => { if (!controller.signal.aborted) setState({ key, error: error.message }); });
    return () => controller.abort();
  }, [roles, key, retry]);
  const current = state?.key === key ? state : undefined;
  const selected = roles.find(role => role.code === value);
  const allowed = current?.items?.[value];
  const all = new Map(Object.values(current?.items ?? {}).flat().map(p => [p.code,p]));
  const restricted = [...all.values()].filter(p => !allowed?.some(a => a.code === p.code));
  const Icon = selected ? roleIcons[selected.code] : ShieldCheck;
  return <div className="ku-role-selector" data-role={value}><div className="ku-role-tabs" role="group" aria-label="Seleccionar rol">{order.map(code => { const role = roles.find(r => r.code === code); const RoleIcon = roleIcons[code]; return role && <button key={code} type="button" disabled={disabled} aria-pressed={value === code} data-role={code} onClick={() => onChange(code)}><RoleIcon size={16} />{roleLabels[code]}</button>; })}</div>
    <div className="ku-role-overview"><div className="ku-role-intro"><span><Icon size={22} /></span><div><h3>{selected ? roleLabels[selected.code] : "Selecciona un rol"}</h3><p>{selected?.description ?? "Elige el nivel de acceso de la cuenta."}</p></div></div>
      {current?.error ? <p role="alert" className="ku-error">{current.error} <button type="button" className="ku-link" onClick={() => setRetry(v => v + 1)}>Reintentar</button></p> : !current ? <p role="status" className="ku-muted">Cargando permisos…</p> : selected && <div className="ku-permission-columns"><section className="ku-allowed"><h4><Check size={13} /> Permitidos <span>{allowed?.length ?? 0}</span></h4><ul>{allowed?.map(p => <li key={p.code}><Check size={12} /><span>{p.description || p.code}</span></li>)}</ul></section><section className="ku-restricted"><h4><X size={13} /> No incluidos <span>{restricted.length}</span></h4>{restricted.length ? <ul>{restricted.map(p => <li key={p.code}><X size={12} /><span>{p.description || p.code}</span></li>)}</ul> : <p>Sin restricciones entre los permisos disponibles.</p>}</section></div>}
    </div>
  </div>;
}
