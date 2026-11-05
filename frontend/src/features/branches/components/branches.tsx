"use client";
import { useEffect, useRef, useState } from "react";
import { Building2, MapPin, Phone, Pencil, Power, Plus, Search, RefreshCw, UsersRound, Warehouse, Mail, IdCard } from "lucide-react";
import { usersRequest } from "@/features/users/client";
import { UserDialog } from "@/features/users/components/dialog";
import { notify } from "@/lib/notifications";
import { BranchForm } from "./branch-form";
import { branchTypeLabels, type Branch, type BranchPage } from "../contracts";
import type { UserPage } from "@/features/users/contracts";
import { avatarColor } from "@/features/users/presentation";
import "@/features/users/users.css";
import "../branches.css";

export function Branches({ permissions }: { permissions: string[] }) {
  const [search, setSearch] = useState("");
  const [type, setType] = useState("");
  const [city, setCity] = useState("");
  const [code, setCode] = useState("");
  const [sunatCode, setSunatCode] = useState("");
  const [enabled, setEnabled] = useState("");
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ key: string; data?: BranchPage; error?: string }>();
  const [modal, setModal] = useState<{ kind: "create" } | { kind: "edit" | "status"; branch: Branch }>();
  const [opening, setOpening] = useState(false);
  const openingRef = useRef(false);
  const query = new URLSearchParams({ page: String(page), size: "9", ...(search.trim() ? { name: search.trim() } : {}), ...(enabled ? { enabled } : {}), ...(type ? { type } : {}), ...(city.trim() ? { city: city.trim() } : {}), ...(code ? { code } : {}), ...(sunatCode ? { sunatCode } : {}) }).toString();
  const key = `${query}:${revision}`;
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => {
      usersRequest<BranchPage>(`branches?${query}`, "GET", undefined, controller.signal).then(data => { if (page && page >= data.totalPages) { setPage(Math.max(0, data.totalPages - 1)); return; } setState({ key, data }); }).catch(err => { if (!controller.signal.aborted) setState({ key, error: err.message }); });
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [query, key, page]);
  const current = state?.key === key ? state : undefined;
  const data = current?.data;
  const can = (p: string) => permissions.includes(p);
  const saved = () => { setModal(undefined); setRevision(v => v + 1); };
  async function edit(id: string) {
    if (openingRef.current) return;
    openingRef.current = true; setOpening(true);
    try { setModal({ kind: "edit", branch: await usersRequest<Branch>(`branches/${id}`) }); }
    catch (error) { notify.error(error instanceof Error ? error.message : "No se pudo cargar la sucursal."); }
    finally { openingRef.current = false; setOpening(false); }
  }
  const filtered = !!(search || enabled || type || city || code || sunatCode);
  const clearFilters = () => { setSearch(""); setEnabled(""); setType(""); setCity(""); setCode(""); setSunatCode(""); setPage(0); };
  return <div className="ku-page kb-page">
    <div className="kb-toolbar"><div className="kb-search"><Search size={16} /><label className="sr-only" htmlFor="kb-search">Buscar sucursal por nombre</label><input id="kb-search" placeholder="Buscar por nombre de sucursal…" type="search" maxLength={100} value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} /></div><label className="sr-only" htmlFor="kb-type-filter">Tipo de sucursal</label><select id="kb-type-filter" value={type} onChange={e => { setType(e.target.value); setPage(0); }}><option value="">Todos los tipos</option><option value="VENTA">Venta</option><option value="ALMACEN">Almacén</option><option value="MIXTO">Mixto</option></select><label className="sr-only" htmlFor="kb-enabled">Estado de sucursales</label><select id="kb-enabled" value={enabled} onChange={e => { setEnabled(e.target.value); setPage(0); }}><option value="">Todos los estados</option><option value="true">Activas</option><option value="false">Inactivas</option></select><button className="ku-icon" aria-label="Actualizar sucursales" disabled={!current} onClick={() => setRevision(v => v + 1)}><RefreshCw size={17} /></button>{can("BRANCH_CREATE") && <button className="ku-button ku-primary kb-new" onClick={() => setModal({ kind: "create" })}><Plus size={17} /> Nueva Sucursal</button>}</div>
    <details className="kb-extra-filters"><summary>Más filtros</summary><div><label htmlFor="kb-code-filter">Código interno<input id="kb-code-filter" maxLength={20} value={code} onChange={e => { setCode(e.target.value.toUpperCase()); setPage(0); }} /></label><label htmlFor="kb-city-filter">Ciudad<input id="kb-city-filter" maxLength={100} value={city} onChange={e => { setCity(e.target.value); setPage(0); }} /></label><label htmlFor="kb-sunat-filter">Código SUNAT<input id="kb-sunat-filter" inputMode="numeric" maxLength={4} value={sunatCode} onChange={e => { setSunatCode(e.target.value); setPage(0); }} /></label>{filtered && <button className="ku-button" onClick={clearFilters}>Limpiar filtros</button>}</div></details>
    <h2 className="sr-only">Sucursales</h2>
    {!current && <div role="status" className="kb-grid">{[0,1,2].map(i => <div className="admin-card kb-loading" key={i}><div className="ku-skeleton" /><p>Cargando sucursales…</p></div>)}</div>}
    {current?.error && <p role="alert" className="ku-error">{current.error} <button className="ku-link" onClick={() => setRevision(v => v + 1)}>Reintentar</button></p>}
    {data && !data.content.length && <section className="admin-card kb-empty"><Building2 size={36} strokeWidth={1.3} /><h3>{filtered ? "No se encontraron sucursales" : "Aún no hay sucursales registradas"}</h3><p>{filtered ? "Ajusta la búsqueda o el filtro de estado." : "Registra la primera sucursal para asignar a tu personal operativo."}</p>{filtered && <button className="ku-button" onClick={clearFilters}>Limpiar filtros</button>}</section>}
    {!!data?.content.length && <div className="kb-grid">{data.content.map(branch => <article className="admin-card kb-card" data-type={branch.branchType ?? "UNCLASSIFIED"} key={branch.id}><header><span className="kb-store">{branch.branchType === "ALMACEN" ? <Warehouse size={19} strokeWidth={1.5} /> : <Building2 size={19} strokeWidth={1.5} />}</span><div className="kb-card-title"><h3>{branch.name}</h3><div><span className="kb-code">{branch.code}</span><span className="ku-enabled" data-enabled={branch.enabled}>{branch.enabled ? "Activa" : "Inactiva"}</span><span className="kb-type">{branch.branchType ? branchTypeLabels[branch.branchType] : "Sin clasificar"}</span></div></div><div className="kb-actions">{can("BRANCH_UPDATE") && <button className="ku-icon" disabled={opening} title="Editar sucursal" aria-label={`Editar sucursal ${branch.name}`} onClick={() => void edit(branch.id)}><Pencil size={15} /></button>}{can("BRANCH_DISABLE") && <button className="ku-icon" title={branch.enabled ? "Desactivar sucursal" : "Reactivar sucursal"} aria-label={`${branch.enabled ? "Desactivar" : "Reactivar"} sucursal ${branch.name}`} onClick={() => setModal({ kind: "status", branch })}><Power size={15} /></button>}</div></header><div className="kb-card-body"><p><MapPin size={15} /><span>{branch.address || "Sin dirección registrada"}{branch.city && ` — ${branch.city}`}{branch.ubigeo && <small className="kb-ubigeo">{branch.ubigeo}</small>}</span></p><p><Phone size={15} /><span>{branch.phone || "Sin teléfono registrado"}</span></p>{branch.email && <p><Mail size={15} /><span>{branch.email}</span></p>}{branch.sunatCode && <p className="kb-sunat"><IdCard size={15} /><span>SUNAT: {branch.sunatCode}</span></p>}{can("USER_READ") && <BranchStaff branchId={branch.id} revision={revision} />}</div></article>)}</div>}
    {data && <footer className="ku-pagination"><span>{data.totalElements} {data.totalElements === 1 ? "sucursal" : "sucursales"}{data.totalPages > 0 && ` · Página ${page + 1} de ${data.totalPages}`}</span><div><button className="ku-button" disabled={!page} onClick={() => setPage(v => v - 1)}>Anterior</button><button className="ku-button" disabled={page + 1 >= data.totalPages} onClick={() => setPage(v => v + 1)}>Siguiente</button></div></footer>}
    {modal?.kind === "create" && <BranchForm onClose={() => setModal(undefined)} onSaved={saved} />}
    {modal?.kind === "edit" && <BranchForm branch={modal.branch} onClose={() => setModal(undefined)} onSaved={saved} />}
    {modal?.kind === "status" && <BranchStatus branch={modal.branch} onClose={() => setModal(undefined)} onSaved={saved} />}
  </div>;
}
function BranchStaff({ branchId, revision }: { branchId: string; revision: number }) {
  const [state,setState] = useState<{ key: string; data?: UserPage; error?: string }>();
  const [retry,setRetry] = useState(0);
  const key = `${branchId}:${revision}:${retry}`;
  useEffect(() => {
    const controller = new AbortController();
    usersRequest<UserPage>(`users?branchId=${branchId}&page=0&size=4`, "GET", undefined, controller.signal).then(data => setState({ key, data })).catch(error => { if (!controller.signal.aborted) setState({ key, error: error.message }); });
    return () => controller.abort();
  }, [branchId,key]);
  const current = state?.key === key ? state : undefined;
  return <section className="kb-staff" aria-label="Personal asignado"><h4><UsersRound size={14} /> Personal</h4>{!current ? <p role="status">Consultando personal…</p> : current.error ? <p>{current.error} <button className="ku-link" onClick={() => setRetry(v => v + 1)}>Reintentar</button></p> : current.data && <><div className="kb-staff-summary"><span>{current.data.totalElements ? `${current.data.totalElements} ${current.data.totalElements === 1 ? "usuario asignado" : "usuarios asignados"}` : "Sin usuarios asignados"}</span><div className="kb-avatar-stack" aria-hidden="true">{current.data.content.map(user => <span style={{ backgroundColor: avatarColor(user.id) }} key={user.id}>{user.firstName.charAt(0)}{user.lastName.charAt(0)}</span>)}{current.data.totalElements > 4 && <span>+{current.data.totalElements - 4}</span>}</div></div>{!!current.data.content.length && <ul>{current.data.content.map(user => <li key={user.id}>{user.firstName} {user.lastName}</li>)}</ul>}</>}</section>;
}
function BranchStatus({ branch, onClose, onSaved }: { branch: Branch; onClose: () => void; onSaved: () => void }) {
  const [busy, setBusy] = useState(false); const [error,setError] = useState(""); const pending = useRef(false);
  async function save() {
    if (pending.current) return; pending.current = true; setBusy(true); setError("");
    try { await usersRequest(`branches/${branch.id}/status`, "PATCH", { enabled: !branch.enabled }); notify.success(branch.enabled ? "Sucursal desactivada correctamente" : "Sucursal reactivada correctamente"); onSaved(); }
    catch (err) { const message = err instanceof Error ? err.message : "No se pudo actualizar la sucursal."; setError(message); notify.error(message); }
    finally { pending.current = false; setBusy(false); }
  }
  return <UserDialog title={branch.enabled ? "Desactivar sucursal" : "Reactivar sucursal"} className="kb-dialog" busy={busy} onClose={onClose}><p>{branch.name}</p><p className="ku-muted">{branch.enabled ? "La sucursal dejará de estar disponible para nuevas asignaciones. No puede desactivarse si tiene trabajadores operativos activos." : "La sucursal volverá a estar disponible para asignar personal."}</p>{error && <p role="alert" className="ku-error">{error}</p>}<div className="ku-dialog-actions"><button className="ku-button" onClick={onClose} disabled={busy}>Cancelar</button><button className={`ku-button ${branch.enabled ? "ku-danger" : "ku-primary"}`} disabled={busy} onClick={() => void save()}>{busy ? "Procesando…" : "Confirmar"}</button></div></UserDialog>;
}
