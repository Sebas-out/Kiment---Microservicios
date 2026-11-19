"use client";

import { useEffect, useRef, useState } from "react";
import { Image as ImageIcon, Plus, X } from "lucide-react";
import { Swatch, type Attribute } from "./attribute-drawer";

export type ColorImages = Record<string, (string | null)[]>;

export function MediaDrawer({ image, galleries, colors, onClose, onSave }: {
  image: string;
  galleries: ColorImages;
  colors: Attribute[];
  onClose: () => void;
  onSave: (image: string, galleries: ColorImages) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [globalPhoto, setGlobalPhoto] = useState(image);
  const [photos, setPhotos] = useState(galleries);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const dirty = globalPhoto !== image || photos !== galleries;

  useEffect(() => {
    const node = dialog.current!;
    const trigger = document.activeElement;
    node.showModal();
    return () => {
      node.close();
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
    };
  }, []);

  function setPhoto(color: string | null, position: number, value: string | null) {
    if (color === null) setGlobalPhoto(value ?? "");
    else setPhotos(previous => {
      const slots = Array.from({ length: 5 }, (_, index) => previous[color]?.[index] ?? null);
      slots[position] = value;
      return { ...previous, [color]: slots };
    });
  }

  async function upload(file: File, color: string | null, position: number) {
    setError("");
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type) || !file.size || file.size > 1024 * 1024) {
      setError("Selecciona un JPG, PNG o WebP de hasta 1 MB.");
      return;
    }
    setBusy(true);
    try {
      const bitmap = await createImageBitmap(file);
      bitmap.close();
      const data = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(reader.error);
        reader.readAsDataURL(file);
      });
      setPhoto(color, position, data);
    } catch { setError("No se pudo abrir la imagen."); }
    finally { setBusy(false); }
  }

  function slot(color: string | null, position: number, value?: string | null) {
    const name = color === null ? "imagen global" : `imagen ${position + 1} de ${color}`;
    return <div className="kp-media-slot" key={position}>
      <label className="kp-media-target" data-filled={!!value}>
        {value ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={value} alt="" />
        ) : <><ImageIcon size={20} aria-hidden="true" /><Plus size={14} className="kp-media-plus" aria-hidden="true" /></>}
        <input type="file" className="sr-only" aria-label={(value ? "Reemplazar " : "Añadir ") + name} disabled={busy}
          accept="image/jpeg,image/png,image/webp" onChange={event => {
            const file = event.currentTarget.files?.[0];
            event.currentTarget.value = "";
            if (file) void upload(file, color, position);
          }} />
      </label>
      {value && <button type="button" className="kp-media-remove" aria-label={"Quitar " + name} disabled={busy} onClick={() => setPhoto(color, position, null)}><X size={13} /></button>}
    </div>;
  }

  return <dialog ref={dialog} className="kp kp-drawer kp-media-drawer" aria-labelledby="kp-media-title"
    onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <header className="kp-drawer-header">
      <div><h2 id="kp-media-title"><ImageIcon size={20} aria-hidden="true" /> Multimedia</h2>
        <p className="kp-muted">Opcional: sube una imagen global del producto y hasta 5 imágenes por color.</p></div>
      <button type="button" aria-label="Cerrar multimedia" disabled={busy} onClick={onClose}><X size={17} /></button>
    </header>
    <div className="kp-drawer-body kp-media-body" aria-busy={busy}>
      <section className="kp-card kp-media-section">
        <h3>Imagen global del producto</h3>
        <p className="kp-muted">Foto principal opcional, independiente de colores o variantes.</p>
        {slot(null, 0, globalPhoto)}
      </section>
      {colors.map(color => (
        <section key={color.name} className="kp-card kp-media-section">
          <div className="kp-between"><h3><Swatch color={color} /> {color.name}</h3>
            <span className="kp-badge">{(photos[color.name] ?? []).slice(0, 5).filter(Boolean).length} / 5</span></div>
          <div className="kp-media-grid">{Array.from({ length: 5 }, (_, index) => slot(color.name, index, photos[color.name]?.[index]))}</div>
        </section>
      ))}
      {!colors.length && <p className="kp-muted">Selecciona colores en el editor para añadir imágenes por color.</p>}
      {error && <p role="alert" className="kp-media-error">{error}</p>}
    </div>
    <footer className="kp-drawer-footer"><button type="button" className="kp-primary" disabled={!dirty || busy}
      onClick={() => onSave(globalPhoto, photos)}>{busy ? "Cargando imagen…" : "Guardar cambios"}</button></footer>
  </dialog>;
}
