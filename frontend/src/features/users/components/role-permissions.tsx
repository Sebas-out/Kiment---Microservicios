"use client";
import { useEffect, useState } from "react";
import { ShieldCheck } from "lucide-react";
import { usersRequest } from "../client";
import type { PermissionRecord } from "../contracts";

export function RolePermissions({ id }: { id?: number }) {
  const [state, setState] = useState<{ id: number; items?: PermissionRecord[]; error?: string }>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    usersRequest<PermissionRecord[]>(`roles/${id}/permissions`, "GET", undefined, controller.signal)
      .then(items => setState({ id, items }))
      .catch(error => { if (!controller.signal.aborted) setState({ id, error: error.message }); });
    return () => controller.abort();
  }, [id, retry]);
  if (!id) return <p className="ku-muted">Selecciona un rol para consultar sus permisos.</p>;
  const current = state?.id === id ? state : undefined;
  return <section className="ku-permissions" aria-label="Permisos del rol"><h3><ShieldCheck size={16} /> Permisos del rol</h3>
    {!current && <p role="status">Cargando permisos…</p>}
    {current?.error && <p role="alert">{current.error} <button type="button" className="ku-link" onClick={() => setRetry(v => v + 1)}>Reintentar</button></p>}
    {current?.items && (current.items.length ? <ul>{current.items.map(p => <li key={p.code}><span>{p.description || p.code}</span><small>{p.code}</small></li>)}</ul> : <p>Este rol no tiene permisos asociados.</p>)}
  </section>;
}
