"use client";

import { useEffect, useRef, useState } from "react";
import { ChevronDown, Plus, X } from "lucide-react";
import { createPortal } from "react-dom";

export function CategoryPicker({ value, options, onSelect, onCreate }: {
  value: string; options: string[]; onSelect: (value: string) => void;
  onCreate: (name: string, description: string) => boolean;
}) {
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const search = useRef<HTMLInputElement>(null);
  const modal = useRef<HTMLDialogElement>(null);
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [query, setQuery] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState(false);
  const normalize = (text: string) => text.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
  const filtered = options.filter(item => normalize(item).includes(normalize(query.trim())));

  useEffect(() => {
    if (!open) return;
    search.current?.focus();
    function outside(event: PointerEvent) {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener("pointerdown", outside);
    return () => document.removeEventListener("pointerdown", outside);
  }, [open]);
  useEffect(() => {
    if (!creating) return;
    const node = modal.current!;
    const button = trigger.current;
    node.showModal();
    return () => { node.close(); button?.focus(); };
  }, [creating]);

  return <div ref={root} className="kp-category" onBlur={event => {
    if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
  }} onKeyDown={event => {
    if (!open || creating) return;
    if (event.key === "Escape") {
      event.stopPropagation(); setOpen(false); trigger.current?.focus();
    }
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      const items = Array.from(root.current?.querySelectorAll<HTMLButtonElement>(".kp-category-menu button") ?? []);
      if (!items.length) return;
      event.preventDefault();
      const index = items.indexOf(document.activeElement as HTMLButtonElement);
      items[index < 0 ? (event.key === "ArrowDown" ? 0 : items.length - 1) : (index + (event.key === "ArrowDown" ? 1 : items.length - 1)) % items.length]?.focus();
    }
  }}>
    <span className="kp-category-label" id="kp-category-label">Categoría *</span>
    <button ref={trigger} type="button" className="kp-category-trigger" aria-labelledby="kp-category-label kp-category-value"
      aria-expanded={open} aria-controls={open ? "kp-category-menu" : undefined} onClick={() => { setQuery(""); setOpen(!open); }}>
      <span id="kp-category-value">{value || "Selecciona categoría"}</span><ChevronDown size={14} />
    </button>
    {open && <div className="kp-category-menu" id="kp-category-menu" role="group" aria-label="Opciones de categoría">
      <input ref={search} aria-label="Buscar categoría" placeholder="Buscar categoría…" value={query}
        onChange={event => setQuery(event.target.value)} onKeyDown={event => { if (event.key === "Enter") event.preventDefault(); }} />
      <button type="button" className="kp-category-create" onClick={() => {
        setName(""); setDescription(""); setError(false); setOpen(false); setCreating(true);
      }}><Plus size={16} /> Nueva categoría</button>
      <div className="kp-category-options">
        {filtered.map(item => <button type="button" aria-pressed={item === value} key={item} onClick={() => {
          onSelect(item); setOpen(false); trigger.current?.focus();
        }}>{item}</button>)}
        {!filtered.length && <p className="kp-muted">Sin resultados</p>}
      </div>
    </div>}
    {creating && createPortal(<dialog ref={modal} className="kp kp-attribute-modal kp-category-modal" aria-labelledby="kp-new-category"
      onCancel={event => { event.preventDefault(); setCreating(false); }}>
      <form onSubmit={event => {
        event.preventDefault();
        event.stopPropagation();
        if (!name.trim()) return;
        if (onCreate(name.trim(), description.trim())) setCreating(false);
        else setError(true);
      }}>
        <header className="kp-between"><h2 id="kp-new-category">Nueva categoría</h2>
          <button type="button" aria-label="Cerrar categoría" onClick={() => setCreating(false)}><X size={16} /></button>
        </header>
        <p className="kp-muted">Completa los datos para crear una nueva categoría.</p>
        <label>Nombre<input required maxLength={80} placeholder="Ej. Polos" value={name} onChange={event => setName(event.target.value)} /></label>
        <label>Descripción<textarea rows={2} maxLength={500} placeholder="Ej. Línea casual" value={description} onChange={event => setDescription(event.target.value)} /></label>
        {error && <p role="alert">No se pudo guardar la categoría en el navegador.</p>}
        <footer><button type="button" onClick={() => setCreating(false)}>Cancelar</button>
          <button className="kp-primary" disabled={!name.trim()}>Guardar</button></footer>
      </form>
    </dialog>, document.body)}
  </div>;
}
