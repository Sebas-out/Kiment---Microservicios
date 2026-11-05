"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Factory, Search, ChevronDown, ChevronLeft, Menu, X, LogOut, LoaderCircle } from "lucide-react";
import { AccountAvatar } from "@/features/account/avatar-state";
import { Brand } from "@/components/brand";
import { ThemeToggle } from "@/components/theme-toggle";
import { navigation } from "@/features/navigation/config";
import { roleLabels, type SessionIdentity } from "@/features/auth/contracts";
import { logoutSession } from "@/features/auth/session-client";
import { notify } from "@/lib/notifications";

function normalize(value: string) {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
}

export function AdminShell({ identity, allowedIds, children }: {
  identity: SessionIdentity; allowedIds: string[]; children: React.ReactNode;
}) {
  const pathname = usePathname();
  const router = useRouter();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [closedGroups, setClosedGroups] = useState<string[]>([]);
  const [query, setQuery] = useState("");
  const [profileOpen, setProfileOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const logoutPending = useRef(false);
  const mobileWasOpen = useRef(false);
  const sidebarRef = useRef<HTMLElement>(null);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const moduleSearchRef = useRef<HTMLInputElement>(null);
  const topSearchRef = useRef<HTMLInputElement>(null);
  const profileRef = useRef<HTMLDivElement>(null);
  const profileButtonRef = useRef<HTMLButtonElement>(null);
  const logoutRef = useRef<HTMLButtonElement>(null);
  const isUsers = pathname === "/usuarios";
  const isAccount = pathname === "/mi-cuenta";
  const title = isAccount ? "Configuración de Cuenta" : isUsers ? "Usuarios" : pathname === "/sucursales" ? "Sucursales" : "Dashboard";
  const section = isAccount ? "Configuración" : isUsers || pathname === "/sucursales" ? "Administración" : "Principal";
  const fullName = `${identity.firstName} ${identity.lastName}`;
  const filteredGroups = navigation.map(group => ({
    ...group, items: group.items.filter(item => allowedIds.includes(item.id) &&
      (!query || normalize(`${group.label} ${item.label}`).includes(normalize(query.trim())))),
  })).filter(group => group.items.length > 0);

  useEffect(() => {
    function shortcut(event: KeyboardEvent) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        if (window.matchMedia("(max-width: 900px)").matches) setMobileOpen(true);
        else topSearchRef.current?.focus();
      }
    }
    const mobile = window.matchMedia("(max-width: 900px)");
    function viewportChanged() {
      if (mobile.matches) setCollapsed(false);
      else setMobileOpen(false);
    }
    document.addEventListener("keydown", shortcut);
    mobile.addEventListener("change", viewportChanged);
    return () => { document.removeEventListener("keydown", shortcut); mobile.removeEventListener("change", viewportChanged); };
  }, []);

  useEffect(() => {
    if (!mobileOpen) {
      if (mobileWasOpen.current) menuButtonRef.current?.focus();
      mobileWasOpen.current = false;
      return;
    }
    mobileWasOpen.current = true;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    moduleSearchRef.current?.focus();
    function trap(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMobileOpen(false); menuButtonRef.current?.focus();
      }
      if (event.key === "Tab") {
        const nodes = sidebarRef.current?.querySelectorAll<HTMLElement>('a[href], button:not(:disabled), input');
        if (!nodes?.length) return;
        const first = nodes[0]; const last = nodes[nodes.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      }
    }
    document.addEventListener("keydown", trap);
    return () => { document.body.style.overflow = previousOverflow; document.removeEventListener("keydown", trap); };
  }, [mobileOpen]);

  useEffect(() => {
    if (!profileOpen) return;
    function outside(event: PointerEvent) {
      if (!profileRef.current?.contains(event.target as Node)) setProfileOpen(false);
    }
    function escape(event: KeyboardEvent) {
      if (event.key === "Escape") { setProfileOpen(false); profileButtonRef.current?.focus(); }
    }
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    logoutRef.current?.focus();
    return () => { document.removeEventListener("pointerdown", outside); document.removeEventListener("keydown", escape); };
  }, [profileOpen]);

  async function signOut() {
    if (logoutPending.current) return;
    logoutPending.current = true; setSigningOut(true);
    try {
      const result = await logoutSession();
      setProfileOpen(false);
      if (result.revoked) notify.info("Sesión cerrada correctamente", { id: "auth:session" });
      else notify.warning("Sesión cerrada en este dispositivo. No se pudo confirmar el cierre remoto.", { id: "auth:session" });
      router.replace("/"); router.refresh();
    } catch (error) {
      notify.error(error instanceof Error ? error.message : "No se pudo cerrar la sesión.", { id: "auth:session" });
    } finally { logoutPending.current = false; setSigningOut(false); }
  }

  return <div className="admin-shell" data-collapsed={collapsed} data-mobile-open={mobileOpen}>
    <a href="#admin-content" className="admin-skip-link">Saltar al contenido</a>
    {mobileOpen && <button className="admin-mobile-overlay" aria-label="Cerrar navegación" onClick={() => { setMobileOpen(false); menuButtonRef.current?.focus(); }} tabIndex={-1} />}
    <aside id="admin-sidebar" className="admin-sidebar" ref={sidebarRef} aria-label="Navegación principal"
      role={mobileOpen ? "dialog" : undefined} aria-modal={mobileOpen || undefined}>
      <div className="admin-sidebar-brand">
        <span className="admin-brand-icon" aria-hidden="true"><Factory size={20} strokeWidth={1.3} /></span>
        <Brand />
        <button type="button" className="admin-collapse-button" onClick={() => setCollapsed(!collapsed)}
          aria-label={collapsed ? "Expandir barra lateral" : "Contraer barra lateral"} aria-expanded={!collapsed} aria-controls="admin-sidebar-nav">
          <ChevronLeft size={17} strokeWidth={1.4} />
        </button>
        <button type="button" className="admin-mobile-close" aria-label="Cerrar navegación" onClick={() => { setMobileOpen(false); menuButtonRef.current?.focus(); }}><X size={19} /></button>
      </div>
      <div className="admin-sidebar-scroll">
        <div className="admin-sidebar-search">
          <Search size={16} strokeWidth={1.5} aria-hidden="true" />
          <input id="sidebar-search" ref={moduleSearchRef} value={query} onChange={event => setQuery(event.target.value)}
            placeholder="Buscar módulo..." aria-label="Buscar módulo en la barra lateral" />
          {query && <button type="button" onClick={() => setQuery("")} aria-label="Limpiar búsqueda"><X size={14} /></button>}
        </div>
        <nav id="admin-sidebar-nav" aria-label="Módulos">
          {filteredGroups.map(group => {
            const open = !!query || !closedGroups.includes(group.id) || collapsed;
            return <section className="admin-nav-group" key={group.id}>
              <button type="button" className="admin-nav-group-heading" disabled={collapsed} aria-expanded={open} aria-controls={`nav-${group.id}`}
                title={group.label} onClick={() => setClosedGroups(current => current.includes(group.id) ? current.filter(id => id !== group.id) : [...current, group.id])}>
                <span>{group.label}</span><ChevronDown size={13} strokeWidth={1.3} data-open={open} />
              </button>
              {open && <ul id={`nav-${group.id}`}>
                {group.items.map(item => {
                  const Icon = item.icon;
                  const active = item.href === pathname;
                  const content = <><Icon size={18} strokeWidth={1.45} aria-hidden="true" /><span className="admin-nav-label">{item.label}</span></>;
                  return <li key={item.id}>
                    {item.href ? <Link href={item.href} className="admin-nav-item" data-active={active} aria-current={active ? "page" : undefined}
                      title={item.label} onClick={() => setMobileOpen(false)}>{content}</Link>
                      : <button type="button" className="admin-nav-item admin-nav-pending" disabled title={`${item.label} · Próximamente`}>
                        {content}<span className="admin-pending-label">Pronto</span>
                      </button>}
                  </li>;
                })}
              </ul>}
            </section>;
          })}
          {!filteredGroups.length && <div className="admin-no-results" role="status">No se encontraron módulos.<button type="button" onClick={() => setQuery("")}>Limpiar búsqueda</button></div>}
        </nav>
      </div>
    </aside>
    <div className="admin-workspace" inert={mobileOpen || undefined}>
      <header className="admin-topbar">
        <button type="button" className="admin-mobile-menu" aria-label="Abrir navegación" aria-expanded={mobileOpen} aria-controls="admin-sidebar" ref={menuButtonRef} onClick={() => setMobileOpen(true)}><Menu size={21} /></button>
        <div className="admin-breadcrumb" aria-label="Ubicación actual"><span>{section}</span><i aria-hidden="true" /><h1>{title}</h1></div>
        <div className="admin-topbar-actions">
          <div className="admin-topbar-search"><Search size={15} strokeWidth={1.4} aria-hidden="true" />
            <input ref={topSearchRef} value={query} onChange={event => { setCollapsed(false); setQuery(event.target.value); }} placeholder="Buscar módulo..." aria-label="Buscar módulo" />
            <kbd title="Control o Comando más K">⌘K</kbd>
          </div>
          <ThemeToggle />
          <div className="admin-profile" ref={profileRef}>
            <button type="button" className="admin-profile-button" ref={profileButtonRef} aria-label={`Perfil de ${fullName}`} aria-haspopup="menu" aria-expanded={profileOpen} aria-controls={profileOpen ? "profile-menu" : undefined} onClick={() => setProfileOpen(!profileOpen)}>
              <AccountAvatar className="admin-avatar" firstName={identity.firstName} lastName={identity.lastName} online />
              <span className="admin-profile-text"><strong title={fullName}>{fullName}</strong><small>{roleLabels[identity.role]}</small></span>
              <ChevronDown size={13} className="admin-profile-chevron" aria-hidden="true" />
            </button>
            {profileOpen && <div id="profile-menu" className="admin-profile-menu" role="menu" aria-label="Perfil y sesión">
              <div className="admin-profile-details" role="presentation"><strong>{fullName}</strong><span>{identity.email}</span><small>{roleLabels[identity.role]}</small></div>
              <button type="button" role="menuitem" onClick={signOut} disabled={signingOut} ref={logoutRef}>
                {signingOut ? <LoaderCircle size={17} className="animate-spin" /> : <LogOut size={17} />} {signingOut ? "Cerrando sesión…" : "Cerrar sesión"}
              </button>
            </div>}
          </div>
        </div>
      </header>
      <main id="admin-content" className="admin-content" tabIndex={-1}>{children}</main>
    </div>
  </div>;
}
