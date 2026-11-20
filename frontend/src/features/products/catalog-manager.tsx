"use client";

import { Pagination } from "@/components/admin/pagination";

import { useEffect, useRef, useState } from "react";
import { Layers3, Palette, Pencil, Plus, Search, Tag, Trash2, X } from "lucide-react";
import { Swatch, type Attribute } from "./attribute-drawer";
import { normalizeCatalog, type Kind } from "./catalog-store";
import { allRecords, catalogRequest, type AttributeRecord } from "./catalog-api";

const labels = {
  categories: { single: "categoría", plural: "categorías", newLabel: "Nueva categoría", icon: Layers3 },
  sizes: { single: "talla", plural: "tallas", newLabel: "Nueva talla", icon: Tag },
  colors: { single: "color", plural: "colores", newLabel: "Nuevo color", icon: Palette },
};

export function CatalogManager({ account, kind, canEdit }: { account: string; kind: Kind; canEdit: boolean }) {
  const [catalog, setCatalog] = useState<AttributeRecord[]>();
  const [loadError, setLoadError] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [action, setAction] = useState<{ mode: "new" | "edit" | "delete"; item?: Attribute }>();
  const { single, plural, newLabel, icon: Icon } = labels[kind];
  useEffect(() => {
    let active = true;
    allRecords<AttributeRecord>(kind + "?enabled=true").then(data => { if (active) setCatalog(data); }).catch(error => { if (active) setLoadError(error.message); });
    return () => { active = false; };
  }, [kind, account]);
  const current = { categoryDescriptions: Object.fromEntries((catalog ?? []).map(item => [item.name, item.description ?? ""])) };
  const items = catalog ?? [];
  const filtered = items.filter(item => normalizeCatalog(item.name + " " + (item.hex ?? "") + " " + (current.categoryDescriptions?.[item.name] ?? "")).includes(normalizeCatalog(query)));
  const total = Math.max(1, Math.ceil(filtered.length / 10));
  const activePage = Math.min(page, total - 1);

  async function commit(name: string, description: string, hex: string) {
    if (!canEdit) return "No tienes permiso para editar este apartado.";
    try {
      const item = catalog?.find(item => item.name === action?.item?.name);
      const remove = action?.mode === "delete";
      await catalogRequest(kind + (item ? "/" + item.id + (remove ? "/status" : "") : ""), remove ? "PATCH" : item ? "PUT" : "POST",
        remove ? { enabled: false } : { name, ...(kind === "categories" ? { description } : kind === "colors" ? { hex } : {}) });
      setCatalog(await allRecords<AttributeRecord>(kind + "?enabled=true"));
      setAction(undefined);
    } catch (error) { return error instanceof Error ? error.message : "No se pudo guardar."; }
  }

  return <div className="kp kp-catalog">
    {loadError && <p role="alert">{loadError}</p>}
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
      {(!catalog || !filtered.length) && <p className="kp-empty">{catalog ? "Sin resultados" : loadError ? "No se pudo cargar el catálogo." : "Cargando catálogo…"}</p>}
    </div>
    <Pagination total={catalog ? filtered.length : 0} label={plural} page={activePage + 1} pages={total} disabled={!catalog} onChange={value => setPage(value - 1)} />
    {canEdit && action && <CatalogModal title={action.mode === "new" ? newLabel : (action.mode === "edit" ? "Editar " : "Eliminar ") + single}
      kind={kind} mode={action.mode} item={action.item} description={current.categoryDescriptions?.[action.item?.name ?? ""] ?? ""}
      onClose={() => setAction(undefined)} onSubmit={commit} />}
  </div>;
}

function CatalogModal({ title, kind, mode, item, description, onClose, onSubmit }: {
  title: string; kind: Kind; mode: string; item?: Attribute; description: string; onClose: () => void;
  onSubmit: (name: string, description: string, hex: string) => Promise<string | undefined>;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  const [name, setName] = useState(item?.name ?? "");
  const [details, setDetails] = useState(description);
  const [hex, setHex] = useState(item?.hex ?? "#000000");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const remove = mode === "delete";
  useEffect(() => {
    const dialog = ref.current!;
    const previous = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (previous instanceof HTMLElement && previous.isConnected) previous.focus(); };
  }, []);
  return <dialog ref={ref} className="kp kp-category-modal kp-attribute-modal" aria-labelledby="kp-catalog-title"
    onCancel={event => { event.preventDefault(); onClose(); }}>
    <form onSubmit={async event => { event.preventDefault(); if (saving) return; setSaving(true); try { setError(await onSubmit(name.trim(), details.trim(), hex.toUpperCase()) ?? ""); } finally { setSaving(false); } }}>
      <header className="kp-between"><h2 id="kp-catalog-title">{title}</h2><button type="button" aria-label="Cerrar" onClick={onClose}><X size={16} /></button></header>
      {remove ? <p>¿Eliminar «{item?.name}» del catálogo?</p> : <>
        <p className="kp-muted">Completa los datos del {kind === "colors" ? "color" : "registro"}.</p>
        <label>Nombre<input required maxLength={kind === "categories" ? 80 : 40} value={name} onChange={event => setName(event.target.value)} placeholder={kind === "colors" ? "Ej. Azul marino" : kind === "sizes" ? "Ej. XL" : "Ej. Polos"} /></label>
        {kind === "categories" && <label>Descripción<textarea rows={2} maxLength={500} placeholder="Ej. Línea casual" value={details} onChange={event => setDetails(event.target.value)} /></label>}
        {kind === "colors" && <label>Código HEX<span className="kp-hex"><input required pattern="#[0-9A-Fa-f]{6}" maxLength={7} value={hex} onChange={event => setHex(event.target.value)} />
          <input type="color" aria-label="Tono del color" value={/^#[0-9a-f]{6}$/i.test(hex) ? hex : "#000000"} onChange={event => setHex(event.target.value)} /></span></label>}
      </>}
      {error && <p role="alert">{error}</p>}
      <footer><button type="button" onClick={onClose}>Cancelar</button><button className="kp-primary" disabled={saving || (!remove && !name.trim())}>{remove ? "Eliminar" : "Guardar"}</button></footer>
    </form>
  </dialog>;
}