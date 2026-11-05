"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Pencil, Plus, RefreshCw, Shield, Building2, ChevronRight, Search, UserRoundCheck, UserRoundX, UsersRound } from "lucide-react";
import { roleLabels } from "@/features/auth/contracts";
import { notify } from "@/lib/notifications";
import { usersRequest, UsersError } from "../client";
import { userQuery, type RoleRecord, type UserPage, type UserRecord } from "../contracts";
import { useBranchOptions } from "@/features/branches/use-branch-options";
import { WorkerAvatar } from "./worker-avatar";
import { UserForm } from "./user-form";
import { AccountAction } from "./account-action";
import { UserDetail } from "./user-detail";
import "../users.css";

type Modal = { kind: "create" } | { kind: "edit" | "role" | "status" | "branch"; user: UserRecord };
export function Users({ actorId, permissions }: { actorId: string; permissions: string[] }) {
  const [search, setSearch] = useState("");
  const mode = search.includes("@") ? "email" : /^[0-9]{8}$/.test(search.trim()) ? "dni" : "name";
  const [branchId, setBranchId] = useState("");
  const branches = useBranchOptions(permissions.includes("BRANCH_READ"), false);
  const [role, setRole] = useState("");
  const [enabled, setEnabled] = useState("");
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ key: string; data?: UserPage; error?: string; status?: number }>();
  const [roleState, setRoleState] = useState<{ data?: RoleRecord[]; error?: string }>({});
  const [roleRetry, setRoleRetry] = useState(0);
  const [selected, setSelected] = useState<string>();
  const [modal, setModal] = useState<Modal>();
  const [opening, setOpening] = useState(false);
  const openingRef = useRef(false);
  const query = userQuery(search, mode, role, enabled, page, branchId).toString();
  const key = `${query}:${revision}`;
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => {
      usersRequest<UserPage>(`users?${query}`, "GET", undefined, controller.signal)
        .then(data => {
          if (data.totalPages && page >= data.totalPages) { setPage(data.totalPages - 1); return; }
          if (!data.totalPages && page) { setPage(0); return; }
          setState({ key, data });
        }).catch(err => { if (!controller.signal.aborted) setState({ key, error: err.message, status: err instanceof UsersError ? err.status : 503 }); });
    }, 300);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [query, key, page]);
  useEffect(() => {
    const controller = new AbortController();
    usersRequest<RoleRecord[]>("roles", "GET", undefined, controller.signal).then(data => setRoleState({ data })).catch(err => { if (!controller.signal.aborted) setRoleState({ error: err.message }); });
    return () => controller.abort();
  }, [roleRetry]);
  const current = state?.key === key ? state : undefined;
  const roles = roleState.data ?? [];
  const data = current?.data;
  const can = (permission: string) => permissions.includes(permission);
  const saved = (user: UserRecord) => { setModal(undefined); setSelected(user.id); setRevision(v => v + 1); };
  async function openEdit(id: string) {
    if (openingRef.current) return;
    openingRef.current = true; setOpening(true);
    try { const user = await usersRequest<UserRecord>(`users/${id}`); setModal({ kind: "edit", user }); }
    catch (err) { notify.error(err instanceof Error ? err.message : "No se pudo cargar el usuario."); }
    finally { openingRef.current = false; setOpening(false); }
  }
  return <div className="ku-page ku-users-page">
    <h2 className="sr-only">Gestión de usuarios</h2>
    {roleState.error && <div role="alert" className="ku-error">No se pudieron cargar los roles. {roleState.error} <button className="ku-link" onClick={() => { setRoleState({}); setRoleRetry(v => v + 1); }}>Reintentar</button></div>}
    {!roleState.data && !roleState.error && <p role="status" className="ku-muted">Cargando roles…</p>}
    <div className="ku-toolbar">
      <div className="ku-quick-search"><Search size={16} /><label className="sr-only" htmlFor="ku-search">Buscar usuarios</label><input id="ku-search" type="search" placeholder="Buscar por nombre, DNI o correo…" maxLength={mode === "email" ? 254 : 160} value={search} onChange={e => { setSearch(e.target.value); setPage(0); }} /></div>
      <label className="sr-only" htmlFor="ku-filter-role">Filtrar por rol</label><select id="ku-filter-role" value={role} onChange={e => { setRole(e.target.value); setPage(0); }} disabled={!roles.length}><option value="">Todos los roles</option>{roles.map(r => <option value={r.code} key={r.id}>{roleLabels[r.code]}</option>)}</select>
      {permissions.includes("BRANCH_READ") && <><label className="sr-only" htmlFor="ku-filter-branch">Filtrar por sucursal</label><select id="ku-filter-branch" value={branchId} disabled={branches.loading || !!branches.error} onChange={e => { setBranchId(e.target.value); setPage(0); }}><option value="">Todas las sucursales</option>{branches.items?.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select></>}
      <label className="sr-only" htmlFor="ku-filter-status">Filtrar por estado</label><select id="ku-filter-status" value={enabled} onChange={e => { setEnabled(e.target.value); setPage(0); }}><option value="">Todos los estados</option><option value="true">Activos</option><option value="false">Inactivos</option></select>
      <button className="ku-icon" aria-label="Actualizar usuarios" disabled={!current} onClick={() => setRevision(v => v + 1)}><RefreshCw size={17} /></button>
      {can("USER_CREATE") && <button className="ku-button ku-primary" disabled={!roles.length || opening} onClick={() => setModal({ kind: "create" })}><Plus size={17} /> Nuevo Usuario</button>}
    </div>
    {mode === "email" && <p className="ku-filter-help">Ingresa el correo electrónico completo.</p>}
    {permissions.includes("BRANCH_READ") && branches.error && <p role="alert" className="ku-error">No se pudieron consultar las sucursales. {branches.error} <button className="ku-link" onClick={branches.retry}>Reintentar</button></p>}
    <div className="ku-layout"><section className="admin-card ku-list" aria-label="Listado de usuarios">
      <div aria-live="polite" className="ku-status">{!current ? "Cargando usuarios…" : data ? `${data.totalElements} ${data.totalElements === 1 ? "usuario encontrado" : "usuarios encontrados"}` : "No se pudo cargar el listado."}</div>
      {current?.error && <div className="ku-error" role="alert">{current.error} <button className="ku-link" onClick={() => setRevision(v => v + 1)}>Reintentar</button>{current.status === 401 && <Link href="/sesion" className="ku-link">Recuperar sesión</Link>}</div>}
      <div className="ku-table-scroll" tabIndex={0} role="region" aria-label="Tabla de usuarios, desplazable horizontalmente" aria-busy={!current}>
        <table><caption className="sr-only">Cuentas de personal y acciones disponibles</caption><thead><tr><th>Usuario</th><th>DNI</th><th>Correo</th><th>Sucursal</th><th>Rol</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>{!current ? Array.from({ length: 5 }, (_,i) => <tr key={i} aria-hidden="true"><td colSpan={7}><div className="ku-skeleton" /></td></tr>) : data?.content.map(user => <tr key={user.id} data-selected={selected === user.id}>
            <td><button className="ku-name" onClick={() => setSelected(user.id)} aria-label={`Ver detalle de ${user.firstName} ${user.lastName}`}><WorkerAvatar user={user} /><span><strong>{user.firstName} {user.lastName}</strong></span></button></td>
            <td className="ku-cell-muted">{user.dni || "—"}</td><td className="ku-cell-muted">{user.email}</td><td className="ku-cell-branch">{user.role === "ADMIN" ? "Global" : branches.items?.find(b => b.id === user.branchId)?.name ?? (user.branchId ? branches.loading ? "Cargando…" : "Asignada (sin detalle)" : "Pendiente de asignación")}</td>
            <td><span className="ku-role" data-role={user.role}>{roleLabels[user.role]}</span></td><td><span className="ku-enabled" data-enabled={user.enabled}>{user.enabled ? "Activo" : "Inactivo"}</span></td>
            <td><div className="ku-row-actions"><button className="ku-icon" aria-label={`Ver detalle: ${user.email}`} title="Ver detalle" onClick={() => setSelected(user.id)}><ChevronRight size={16} /></button>
              {can("USER_UPDATE") && <button className="ku-icon" aria-label={`Editar: ${user.email}`} title="Editar datos" disabled={opening} onClick={() => void openEdit(user.id)}><Pencil size={16} /></button>}
              {can("USER_ROLE_CHANGE") && <button className="ku-icon" aria-label={`Cambiar rol: ${user.email}`} title={user.id === actorId ? "No puedes cambiar tu propio rol" : "Cambiar rol"} disabled={user.id === actorId || !roles.length || opening} onClick={() => setModal({ kind: "role", user })}><Shield size={16} /></button>}
              {can("USER_UPDATE") && user.role !== "ADMIN" && <button className="ku-icon" aria-label={`Reasignar sucursal: ${user.email}`} title="Reasignar sucursal" disabled={user.id === actorId || opening} onClick={() => setModal({ kind: "branch", user })}><Building2 size={16} /></button>}
              {can("USER_DISABLE") && <button className="ku-icon" aria-label={`${user.enabled ? "Desactivar" : "Reactivar"}: ${user.email}`} title={user.enabled ? "Desactivar usuario" : "Reactivar usuario"} disabled={opening} onClick={() => setModal({ kind: "status", user })}>{user.enabled ? <UserRoundX size={16} /> : <UserRoundCheck size={16} />}</button>}
            </div></td>
          </tr>)}</tbody>
        </table>
      </div>
      {data && !data.content.length && <div className="ku-empty"><UsersRound size={32} /><h3>No se encontraron usuarios</h3><p>Prueba con otro nombre o ajusta los filtros.</p>{(search || role || enabled || branchId) && <button className="ku-button" onClick={() => { setSearch(""); setRole(""); setEnabled(""); setBranchId(""); setPage(0); }}>Limpiar filtros</button>}</div>}
      <footer className="ku-pagination"><span>{data ? `${data.totalElements} ${data.totalElements === 1 ? "usuario" : "usuarios"} · ${data.totalPages ? `Página ${page + 1} de ${data.totalPages}` : "Sin resultados"}` : "Listado de usuarios"}</span><div><button className="ku-button" disabled={!data || !page} onClick={() => setPage(v => v - 1)}>Anterior</button><button className="ku-button" disabled={!data || page + 1 >= data.totalPages} onClick={() => setPage(v => v + 1)}>Siguiente</button></div></footer>
    </section><UserDetail id={selected} revision={revision} roles={roles} branches={branches.items} permissions={permissions} actorId={actorId} onAction={(kind,user) => setModal({ kind, user })} onClose={() => setSelected(undefined)} /></div>
    {opening && <p role="status" className="ku-muted">Cargando datos para editar…</p>}
    {modal?.kind === "create" && <UserForm roles={roles} permissions={permissions} onClose={() => setModal(undefined)} onSaved={saved} />}
    {modal?.kind === "edit" && <UserForm user={modal.user} roles={roles} permissions={permissions} onClose={() => setModal(undefined)} onSaved={saved} />}
    {(modal?.kind === "role" || modal?.kind === "status" || modal?.kind === "branch") && <AccountAction user={modal.user} kind={modal.kind} roles={roles} permissions={permissions} onClose={() => setModal(undefined)} onSaved={saved} />}
  </div>;
}
