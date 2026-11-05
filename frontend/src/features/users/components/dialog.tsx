"use client";
import { useEffect, useRef, type ReactNode } from "react";
import { X } from "lucide-react";

export function UserDialog({ title, children, busy = false, onClose, className = "" }: { title: string; children: ReactNode; busy?: boolean; onClose: () => void; className?: string }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current!;
    const previous = document.activeElement;
    dialog.showModal();
    return () => { dialog.close(); if (previous instanceof HTMLElement && previous.isConnected) previous.focus(); };
  }, []);
  return <dialog ref={ref} className={`ku-dialog ${className}`} aria-labelledby="ku-dialog-title" onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="ku-dialog-heading"><h2 id="ku-dialog-title">{title}</h2><button type="button" className="ku-icon" aria-label="Cerrar diálogo" disabled={busy} onClick={onClose}><X size={20} /></button></div>
    <div className="ku-dialog-body">{children}</div>
  </dialog>;
}
