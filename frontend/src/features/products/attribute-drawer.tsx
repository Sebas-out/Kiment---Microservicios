"use client";

import { Pagination } from "@/components/admin/pagination";

import { useEffect, useRef, useState } from "react";
import { Check, Plus, Search, X } from "lucide-react";

export type Attribute = { name: string; hex?: string };
export type AttributeKind = "colors" | "sizes";
export const defaultColors: Attribute[] = [
  { name: "Rosa nude", hex: "#efd8dd" }, { name: "Cocoa", hex: "#895d4e" },
  { name: "Negro", hex: "#18181b" }, { name: "Camel claro", hex: "#c19a6b" },
  { name: "Azul", hex: "#233b66" }, { name: "Blanco", hex: "#fafafa" },
  { name: "Rosado", hex: "#f9b6c7" }, { name: "Mostaza", hex: "#956329" },
];
export function Swatch({ color }: { color?: Attribute }) {
  return <span className="kp-swatch" aria-hidden="true" style={{ backgroundColor: color?.hex ?? "#94a3b8" }} />;
}

export function AttributeDrawer({ initialKind, options, selected, onToggle, onCreate, onClose }: {
  initialKind: AttributeKind;
  options: Record<AttributeKind, Attribute[]>;
  selected: Record<AttributeKind, string[]>;
  onToggle: (kind: AttributeKind, name: string) => void;
  onCreate: (kind: AttributeKind, value: Attribute) => Promise<boolean>;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const creation = useRef<HTMLDialogElement>(null);
  const [kind, setKind] = useState(initialKind);
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [hex, setHex] = useState("#efd8dd");
  const [error, setError] = useState("");
  const noun = kind === "colors" ? "color" : "talla";
  const normalize = (value: string) => value.trim().toLocaleLowerCase("es");
  const filtered = options[kind].filter(item => normalize(item.name).includes(normalize(query)));
  const total = Math.max(1, Math.ceil(filtered.length / 10));
  const current = Math.min(page, total - 1);

  useEffect(() => {
    const element = dialog.current!;
    const trigger = document.activeElement;
    element.showModal();
    return () => {
      element.close();
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
    };
  }, []);

  useEffect(() => {
    if (!creating) return;
    const modal = creation.current!;
    modal.showModal();
    return () => modal.close();
  }, [creating]);

  const [saving, setSaving] = useState(false);
  async function create() {
    if (saving) return;
    setSaving(true);
    try {
    const value = name.trim();
    if (!value) { setError("Escribe un nombre."); return; }
    const existing = options[kind].find(item => normalize(item.name) === normalize(value));
    if (existing) {
      if (!selected[kind].includes(existing.name)) onToggle(kind, existing.name);
    } else if (!await onCreate(kind, { name: value, ...(kind === "colors" ? { hex } : {}) })) {
      setError("No se pudo guardar el atributo en el navegador.");
      return;
    }
    setCreating(false); setName(""); setQuery(""); setPage(0); setError("");
    } catch (error) { setError(error instanceof Error ? error.message : "No se pudo guardar."); } finally { setSaving(false); }
  }

  return <dialog className="kp kp-drawer" ref={dialog} aria-labelledby="kp-attributes-title"
    onCancel={event => { event.preventDefault(); onClose(); }}>
    <header className="kp-drawer-header">
      <div><h2 id="kp-attributes-title">Atributos del producto</h2><p className="kp-muted">Selecciona colores y tallas para construir la lista completa de variantes.</p></div>
      <button type="button" aria-label="Cerrar atributos" onClick={onClose}><X size={17} /></button>
    </header>
    <div className="kp-drawer-tabs" role="group" aria-label="Tipo de atributo">
      {(["colors", "sizes"] as const).map(tab => <button type="button" key={tab} aria-pressed={kind === tab}
        onClick={() => { setKind(tab); setQuery(""); setPage(0); setCreating(false); setName(""); setError(""); }}>
        {tab === "colors" ? "Colores" : "Tallas"} <span className="kp-badge">{selected[tab].length}</span>
      </button>)}
    </div>
    <div className="kp-drawer-body">
      <div className="kp-toolbar">
        <div className="kp-search"><Search size={15} /><input aria-label={"Buscar " + noun} placeholder={"Buscar " + noun + "…"}
          value={query} onChange={event => { setQuery(event.target.value); setPage(0); }} /></div>
        <button type="button" onClick={() => { setCreating(!creating); setError(""); }}><Plus size={15} />{kind === "colors" ? "Nuevo color" : "Nueva talla"}</button>
      </div>
      {creating && <dialog ref={creation} className="kp kp-attribute-modal" aria-labelledby="kp-new-attribute"
        onCancel={event => { event.preventDefault(); setCreating(false); }}>
        <form onSubmit={event => { event.preventDefault(); create(); }}>
          <header className="kp-between"><h2 id="kp-new-attribute">{kind === "colors" ? "Nuevo color" : "Nueva talla"}</h2>
          <button type="button" aria-label="Cerrar creación" onClick={() => setCreating(false)}><X size={16} /></button></header>
          <p className="kp-muted">Completa los datos para crear {kind === "colors" ? "un nuevo color" : "una nueva talla"}.</p>
          <label>Nombre<input required maxLength={40} placeholder={kind === "colors" ? "Ej. Azul marino" : "Ej. XXL"} value={name} onChange={event => setName(event.target.value)} /></label>
          {kind === "colors" && <label>Código HEX<span className="kp-hex">
            <input required pattern="#[0-9A-Fa-f]{6}" maxLength={7} value={hex} onChange={event => setHex(event.target.value)} />
            <input type="color" aria-label="Tono del color" value={/^#[0-9a-f]{6}$/i.test(hex) ? hex : "#000000"} onChange={event => setHex(event.target.value)} />
          </span></label>}
          {error && <small role="alert">{error}</small>}
          <footer><button type="button" onClick={() => setCreating(false)}>Cancelar</button><button className="kp-primary" disabled={saving || !name.trim()}>Guardar</button></footer>
        </form>
      </dialog>}
      <div className="kp-selected kp-chips">
        {selected[kind].length ? selected[kind].map(item => <button key={item} type="button" onClick={() => onToggle(kind, item)}>
          {kind === "colors" && <Swatch color={options.colors.find(color => color.name === item)} />}{item}<X size={12} />
        </button>) : <p className="kp-muted">{kind === "colors" ? "Sin colores seleccionados." : "Sin tallas seleccionadas."}</p>}
      </div>
      <div className={"kp-attribute-options" + (kind === "sizes" ? " kp-size-options" : "")}>
        {filtered.slice(current * 10, current * 10 + 10).map(item => <button key={item.name} type="button" aria-pressed={selected[kind].includes(item.name)} onClick={() => onToggle(kind, item.name)}>
          {kind === "colors" && <Swatch color={item} />}<span>{item.name}</span>{selected[kind].includes(item.name) && <Check className="kp-attribute-check" size={16} />}
        </button>)}
        {!filtered.length && <p className="kp-muted">No se encontraron resultados.</p>}
      </div>
    </div>
    <div className="kp-drawer-pagination"><Pagination total={filtered.length} label="resultados" page={current + 1} pages={total} onChange={value => setPage(value - 1)} /></div>
    <footer className="kp-drawer-footer kp-muted">Cada combinación de color y talla actualiza la matriz automáticamente.</footer>
  </dialog>;
}
