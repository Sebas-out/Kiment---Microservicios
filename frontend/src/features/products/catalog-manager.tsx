"use client";

import { Pagination } from "@/components/admin/pagination";

import { useEffect, useRef, useState } from "react";
import { Layers3, Palette, Pencil, Plus, Search, Tag, Trash2, X } from "lucide-react";
import { defaultColors, Swatch, type Attribute } from "./attribute-drawer";
import { catalogKeyFor, commonSizes, defaultCategories, normalizeCatalog, readCatalog, visibleCatalog, type Kind } from "./catalog-store";

const labels = {
  categories: { single: "categoría", plural: "categorías", newLabel: "Nueva categoría", icon: Layers3 },
  sizes: { single: "talla", plural: "tallas", newLabel: "Nueva talla", icon: Tag },
  colors: { single: "color", plural: "colores", newLabel: "Nuevo color", icon: Palette },
};

export function CatalogManager({ account, kind, canEdit }: { account: string; kind: Kind; canEdit: boolean }) {
  const key = catalogKeyFor(account);
  const [catalog, setCatalog] = useState<ReturnType<typeof readCatalog>>();
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [action, setAction] = useState<{ mode: "new" | "edit" | "delete"; item?: Attribute }>();
  const { single, plural, newLabel, icon: Icon } = labels[kind];
  useEffect(() => {
    const timer = setTimeout(() => setCatalog(readCatalog(key)), 0);
    const sync = (event: StorageEvent) => { if (event.key === key) setCatalog(readCatalog(key)); };
    window.addEventListener("storage", sync);
    return () => { clearTimeout(timer); window.removeEventListener("storage", sync); };
  }, [key]);

  const current = catalog ?? { colors: [], sizes: [], categories: [] };
  const items = visibleCatalog(kind === "colors" ? [...defaultColors, ...current.colors]
    : (kind === "sizes" ? [...commonSizes, ...current.sizes.map(item => item.name)] : [...defaultCategories, ...current.categories]).map(name => ({ name })), current, kind);
  const filtered = items.filter(item => normalizeCatalog(item.name + " " + (item.hex ?? "") + " " + (current.categoryDescriptions?.[item.name] ?? "")).includes(normalizeCatalog(query)));
  const total = Math.max(1, Math.ceil(filtered.length / 10));
  const activePage = Math.min(page, total - 1);

  function commit(name: string, description: string, hex: string) {
    if (!canEdit) return "No tienes permiso para editar este apartado.";
    const latest = readCatalog(key);
    const old = action?.item?.name;
    const remove = action?.mode === "delete";
    if (!remove && items.some(item => item.name !== old && normalizeCatalog(item.name) === normalizeCatalog(name))) return "Ya existe un registro con ese nombre.";
    try {
      if (old && (remove || name !== old)) {
        const drafts = JSON.parse(localStorage.getItem("kiments:product-drafts:" + account) ?? "[]") as { category: string; variants: { color: string; size: string }[] }[];
        if (drafts.some(draft => kind === "categories" ? draft.category === old : draft.variants.some(row => (kind === "colors" ? row.color : row.size) === old))) {
          return "Este registro está en uso. Retíralo primero de los productos que lo utilizan.";
        }
      }
      const hidden = new Set(latest.hidden?.[kind]);
      if (old && (remove || name !== old)) hidden.add(old);
      if (!remove) hidden.delete(name);
      latest.hidden = { ...latest.hidden, [kind]: [...hidden] };
      if (kind === "categories") {
        latest.categories = latest.categories.filter(item => item !== old);
        const descriptions = { ...latest.categoryDescriptions };
        if (old) delete descriptions[old];
        if (!remove) { latest.categories.push(name); descriptions[name] = description; }
        latest.categoryDescriptions = descriptions;
      } else {
        latest[kind] = latest[kind].filter(item => item.name !== old);
        if (!remove) latest[kind].push({ name, ...(kind === "colors" ? { hex } : {}) });
      }
      localStorage.setItem(key, JSON.stringify(latest));
      setCatalog(latest);
      setAction(undefined);
    } catch { return "No se pudieron guardar los cambios en el navegador."; }
  }

  return <div className="kp kp-catalog">
    <div className="kp-toolbar">
      <label className="kp-search"><Search size={16} /><input aria-label={"Buscar " + single} placeholder={"Buscar " + single + "…"} value={query} onChange={event => { setQuery(event.target.value); setPage(0); }} /></label>
      {canEdit && <button type="button" className="kp-primary" disabled={!catalog} onClick={() => setAction({ mode: "new" })}><Plus size={16} />{newLabel}</button>}
    </div>
    <div className="kp-card kp-catalog-table">
      <table>
        <thead><tr><th>{kind === "sizes" ? "Nombre" : single}</th>{kind === "categories" && <th>Descripción</th>}{kind === "colors" && <><th>Muestra</th><th>Hexadecimal</th></>}<th>Estado</th>{canEdit && <th>Acciones</th>}</tr></thead>
        <tbody>{catalog && filtered.slice(activePage * 10, activePage * 10 + 10).map(item => <tr key={item.name}>
          <td><span className="kp-catalog-name"><span><Icon size={15} /></span>{item.name}</span></td>
          {kind === "categories" && <td className="kp-muted">{current.categoryDescriptions?.[item.name] || "Sin descripción"}</td>}
          {kind === "colors" && <><td><Swatch color={item} /></td><td><code>{item.hex?.toUpperCase() ?? "—"}</code></td></>}
          <td><span className="kp-catalog-active">Activo</span></td>
          {canEdit && <td><div className="kp-catalog-actions">
            <button type="button" aria-label={"Editar " + item.name} onClick={() => setAction({ mode: "edit", item })}><Pencil size={15} /></button>
            <button type="button" aria-label={"Eliminar " + item.name} onClick={() => setAction({ mode: "delete", item })}><Trash2 size={15} /></button>
          </div></td>}
        </tr>)}</tbody>
      </table>
      {(!catalog || !filtered.length) && <p className="kp-empty">{catalog ? "Sin resultados" : "Cargando catálogo…"}</p>}
    </div>
    <Pagination total={catalog ? filtered.length : 0} label={plural} page={activePage + 1} pages={total} disabled={!catalog} onChange={value => setPage(value - 1)} />
    {canEdit && action && <CatalogModal title={action.mode === "new" ? newLabel : (action.mode === "edit" ? "Editar " : "Eliminar ") + single}
      kind={kind} mode={action.mode} item={action.item} description={current.categoryDescriptions?.[action.item?.name ?? ""] ?? ""}
      onClose={() => setAction(undefined)} onSubmit={commit} />}
  </div>;
}

function CatalogModal({ title, kind, mode, item, description, onClose, onSubmit }: {
  title: string; kind: Kind; mode: string; item?: Attribute; description: string; onClose: () => void;
  onSubmit: (name: string, description: string, hex: string) => string | undefined;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [name, setName] = useState(item?.name ?? "");
  const [details, setDetails] = useState(description);
  const [hex, setHex] = useState(item?.hex ?? "#000000");
  const [error, setError] = useState("");
  const remove = mode === "delete";
  useEffect(() => {
    const dialog = ref.current!;
    const previous = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (previous instanceof HTMLElement && previous.isConnected) previous.focus(); };
  }, []);
  return <dialog ref={ref} className="kp kp-category-modal kp-attribute-modal" aria-labelledby="kp-catalog-title"
    onCancel={event => { event.preventDefault(); onClose(); }}>
    <form onSubmit={event => { event.preventDefault(); setError(onSubmit(name.trim(), details.trim(), hex.toUpperCase()) ?? ""); }}>
      <header className="kp-between"><h2 id="kp-catalog-title">{title}</h2><button type="button" aria-label="Cerrar" onClick={onClose}><X size={16} /></button></header>
      {remove ? <p>¿Eliminar «{item?.name}» del catálogo local?</p> : <>
        <p className="kp-muted">Completa los datos del {kind === "colors" ? "color" : "registro"}.</p>
        <label>Nombre<input required maxLength={kind === "categories" ? 80 : 40} value={name} onChange={event => setName(event.target.value)} placeholder={kind === "colors" ? "Ej. Azul marino" : kind === "sizes" ? "Ej. XL" : "Ej. Polos"} /></label>
        {kind === "categories" && <label>Descripción<textarea rows={2} maxLength={500} placeholder="Ej. Línea casual" value={details} onChange={event => setDetails(event.target.value)} /></label>}
        {kind === "colors" && <label>Código HEX<span className="kp-hex"><input required pattern="#[0-9A-Fa-f]{6}" maxLength={7} value={hex} onChange={event => setHex(event.target.value)} />
          <input type="color" aria-label="Tono del color" value={/^#[0-9a-f]{6}$/i.test(hex) ? hex : "#000000"} onChange={event => setHex(event.target.value)} /></span></label>}
      </>}
      {error && <p role="alert">{error}</p>}
      <footer><button type="button" onClick={onClose}>Cancelar</button><button className="kp-primary" disabled={!remove && !name.trim()}>{remove ? "Eliminar" : "Guardar"}</button></footer>
    </form>
  </dialog>;
}