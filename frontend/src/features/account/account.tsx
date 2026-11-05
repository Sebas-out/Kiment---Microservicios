"use client";

import { useRef, useState, type KeyboardEvent } from "react";
import { Building2, CalendarDays, IdCard, LockKeyhole, Mail, Phone, ShieldCheck, UserRound } from "lucide-react";
import { roleLabels, type AccountUser } from "@/features/auth/contracts";
import { AccountAvatar } from "./avatar-state";
import { AvatarDialog } from "./avatar-dialog";
import { PasswordPanel } from "./password-panel";

const locale = "es-PE";
const zone = "America/Lima";
function dateLabel(value: string | null | undefined, short = false) {
  const date = value ? new Date(value) : null;
  if (!date || Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(locale, short
    ? { month: "long", year: "numeric", timeZone: zone }
    : { dateStyle: "medium", timeStyle: "short", timeZone: zone }).format(date);
}

const tabs = [{ id: "profile", label: "Perfil", icon: UserRound }, { id: "security", label: "Seguridad", icon: LockKeyhole }] as const;
type Tab = typeof tabs[number]["id"];

export function Account({ user }: { user: AccountUser }) {
  const [tab, setTab] = useState<Tab>("profile");
  const [showPhoto, setShowPhoto] = useState(false);
  const tablist = useRef<HTMLDivElement>(null);
  const role = roleLabels[user.role];
  const date = dateLabel(user.createdAt);
  const month = dateLabel(user.createdAt, true);
  // /auth/me exposes the assignment ID only. Branch details are ADMIN-only.
  const scope = user.role === "ADMIN" && user.branchId === null ? "Global" : user.branchId ? "Sucursal asignada" : "No disponible";
  const rows = [
    ["Nombre", user.firstName, UserRound], ["Apellido", user.lastName, UserRound],
    ["Correo", user.email, Mail], ["DNI", user.dni, IdCard],
    ["Teléfono", user.phone, Phone], ["Rol", role, ShieldCheck],
    ["Fecha de creación", date, CalendarDays], ["Sucursal", scope, Building2],
  ] as const;

  function switchByKey(event: KeyboardEvent) {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const next = event.key === "Home" ? "profile" : event.key === "End" ? "security" : tab === "profile" ? "security" : "profile";
    setTab(next);
    tablist.current?.querySelector<HTMLButtonElement>(`#ka-tab-${next}`)?.focus();
  }

  return <div className="ka-account">
    <section className="ka-hero" aria-labelledby="ka-name">
      <div className="ka-cover" aria-hidden="true" />
      <div className="ka-identity">
        <div className="ka-badges"><span>{user.enabled ? "Cuenta activa" : "Cuenta inactiva"}</span><span><ShieldCheck size={13} />{role}</span></div>
        <div className="ka-name"><AccountAvatar className="ka-avatar" firstName={user.firstName} lastName={user.lastName} />
          <h2 id="ka-name">{user.firstName} {user.lastName}</h2></div>
        <div className="ka-hero-footer">
          <div className="ka-meta"><span><Mail size={12} />{user.email}</span><span>{scope}</span>{month && <span>Miembro desde {month}</span>}</div>
          <button type="button" className="ka-button ka-primary ka-pill" onClick={() => setShowPhoto(true)}>Cambiar foto de perfil</button>
        </div>
      </div>
    </section>
    <div className="ka-tabs" role="tablist" aria-label="Configuración de cuenta" ref={tablist} onKeyDown={switchByKey}>
      {tabs.map(({ id, label, icon: Icon }) => <button type="button" role="tab" key={id} id={`ka-tab-${id}`} aria-selected={tab === id}
        aria-controls={`ka-panel-${id}`} tabIndex={tab === id ? 0 : -1} onClick={() => setTab(id)}><Icon size={14} aria-hidden="true" />{label}</button>)}
    </div>
    <div role="tabpanel" id="ka-panel-profile" aria-labelledby="ka-tab-profile" hidden={tab !== "profile"}>
      <div className="ka-overview">
        <section className="ka-card">
          <h2>Resumen del perfil</h2><p>Datos clave del usuario autenticado en un formato rápido de consultar.</p>
          <dl className="ka-fields">{rows.map(([label, value, Icon]) => <div className="ka-field" key={label}>
            <span className="ka-field-icon"><Icon size={15} aria-hidden="true" /></span>
            <div><dt>{label}</dt><dd>{value || "No disponible"}</dd></div>
          </div>)}</dl>
          <div className="ka-photo-note"><div><strong>Vista detallada de la cuenta</strong><p>Administra tu foto de perfil desde aquí.</p></div>
            <button type="button" className="ka-button ka-pill" onClick={() => setShowPhoto(true)}>Cambiar foto</button></div>
        </section>
        <section className="ka-card">
          <h2>Estado de la cuenta</h2><p>Información operativa y de pertenencia dentro del sistema.</p>
          <div className="ka-status">
            <dl>
              <div><dt>Rol asignado</dt><dd>{role}</dd></div>
              <div><dt>Sucursal principal</dt><dd>{scope}</dd></div>
              <div><dt>Fecha de alta</dt><dd>{month ?? "No disponible"}</dd></div>
            </dl>
            <p><ShieldCheck size={14} /> Acceso ligado a permisos según tu rol actual.</p>
            {date && <p><CalendarDays size={14} /> Cuenta creada el {date}.</p>}
          </div>
        </section>
      </div>
    </div>
    <div role="tabpanel" id="ka-panel-security" aria-labelledby="ka-tab-security" hidden={tab !== "security"}>
      {tab === "security" && <PasswordPanel />}
    </div>
    {showPhoto && <AvatarDialog user={user} onClose={() => setShowPhoto(false)} />}
  </div>;
}
