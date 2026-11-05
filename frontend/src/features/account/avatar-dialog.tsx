"use client";

import { useEffect, useRef, useState } from "react";
import { ImageIcon, LoaderCircle, Trash2, Upload, X } from "lucide-react";
import type { AccountUser } from "@/features/auth/contracts";
import { notify } from "@/lib/notifications";
import { AccountAvatar, useAccountAvatar } from "./avatar-state";
import { avatarFileProblem, matchesAvatarSignature } from "./avatar-contract";
import { accountMutation } from "./client";

export function AvatarDialog({ user, onClose }: { user: AccountUser; onClose: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const pending = useRef(false);
  const selection = useRef(0);
  const lifetime = useRef<AbortSignal | null>(null);
  const [candidate, setCandidate] = useState<{ file: File; preview: string }>();
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState<"save" | "remove" | null>(null);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState("");
  const { src, update } = useAccountAvatar();
  const preview = candidate?.preview;

  useEffect(() => {
    const node = dialog.current!;
    const previous = document.activeElement;
    const controller = new AbortController();
    lifetime.current = controller.signal;
    node.showModal();
    return () => {
      controller.abort();
      node.close();
      if (previous instanceof HTMLElement && previous.isConnected) previous.focus();
    };
  }, []);

  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  async function choose(file: File) {
    const version = ++selection.current;
    const signal = lifetime.current;
    setCandidate(undefined);
    setError("");
    const invalid = avatarFileProblem(file);
    if (invalid) { setChecking(false); setError(invalid.message); return; }
    setChecking(true);
    try {
      const bytes = new Uint8Array(await file.slice(0, 8).arrayBuffer());
      if (signal?.aborted || version !== selection.current) return;
      if (!matchesAvatarSignature(file.type, bytes)) {
        setError("El archivo no contiene una imagen JPG o PNG válida.");
        return;
      }
      setCandidate({ file, preview: URL.createObjectURL(file) });
    } catch {
      if (!signal?.aborted && version === selection.current) setError("No se pudo leer la imagen seleccionada.");
    } finally {
      if (!signal?.aborted && version === selection.current) setChecking(false);
    }
  }

  async function save(action: "save" | "remove") {
    if (pending.current || (action === "save" && !candidate)) return;
    pending.current = true;
    setBusy(action);
    setError("");
    try {
      const data = action === "save" ? new FormData() : undefined;
      if (data && candidate) data.set("file", candidate.file);
      await accountMutation("avatar", action === "save" ? "POST" : "DELETE", data);
      update(action === "save");
      notify.success(action === "save" ? "Foto de perfil actualizada" : "Foto de perfil eliminada", { id: "account:avatar" });
      onClose();
    } catch (error) {
      setError(error instanceof Error ? error.message : "No se pudo completar la operación.");
    } finally {
      pending.current = false;
      setBusy(null);
    }
  }

  function close() { if (!pending.current) onClose(); }

  return <dialog className="ka-dialog" ref={dialog} aria-labelledby="ka-photo-title" aria-describedby="ka-photo-description"
    onCancel={event => { event.preventDefault(); if (confirm && !pending.current) setConfirm(false); else close(); }}>
    <header className="ka-dialog-heading">
      <div><h2 id="ka-photo-title">{confirm ? "¿Quitar la foto de perfil?" : "Cambiar foto de perfil"}</h2>
        <p id="ka-photo-description">{confirm ? "Tu cuenta volverá a mostrar tus iniciales." : "Sube una imagen nueva o elimina la actual si ya tienes una."}</p></div>
      <button type="button" className="ka-close" aria-label="Cerrar diálogo" onClick={close} disabled={!!busy}><X size={16} /></button>
    </header>
    {!confirm && <div className="ka-dialog-body">
      <div className="ka-photo-banner">
        <div className="ka-preview-frame">
          <AccountAvatar className="ka-avatar ka-avatar-preview" firstName={user.firstName} lastName={user.lastName} preview={preview} />
        </div>
        <div><h3>Foto de perfil</h3><p>Sube una imagen para personalizar tu cuenta. Si ya tienes una, también puedes eliminarla desde aquí.</p><span>JPG o PNG hasta 2 MB</span></div>
      </div>
      <div className="ka-picker">
        <span className="ka-picker-icon"><Upload size={19} aria-hidden="true" /></span>
        <h3>Explorar archivos</h3>
        <p>{checking ? "Validando imagen…" : candidate?.file.name ?? "Selecciona una imagen para actualizar tu perfil."}</p>
        <input ref={input} type="file" accept="image/jpeg,image/png,.jpg,.jpeg,.png" hidden
          onChange={event => { const file = event.currentTarget.files?.[0]; event.currentTarget.value = ""; if (file) void choose(file); }} />
        <button type="button" className="ka-button" disabled={!!busy || checking} onClick={() => input.current?.click()}><ImageIcon size={14} /> Seleccionar imagen</button>
      </div>
    </div>}
    {error && <p className="ka-error ka-dialog-error" role="alert">{error}</p>}
    <footer className="ka-dialog-actions" aria-busy={!!busy}>
      {!confirm && src && <button type="button" className="ka-button ka-remove" disabled={!!busy} onClick={() => { setConfirm(true); setError(""); }}><Trash2 size={14} /> Eliminar foto actual</button>}
      <div>
        <button type="button" className="ka-button" disabled={!!busy} onClick={confirm ? () => { setConfirm(false); setError(""); } : close}>Cancelar</button>
        <button type="button" className={`ka-button ${confirm ? "ka-danger" : "ka-primary"}`} disabled={!!busy || (!confirm && (!candidate || checking))}
          onClick={() => void save(confirm ? "remove" : "save")}>
          {busy && <LoaderCircle size={14} className="animate-spin" />}{busy === "save" ? "Guardando…" : busy === "remove" ? "Quitando…" : confirm ? "Quitar fotografía" : "Guardar foto"}
        </button>
      </div>
    </footer>
  </dialog>;
}
