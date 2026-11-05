"use client";

import { useRef, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { Eye, EyeOff, LoaderCircle } from "lucide-react";
import { accountMutation } from "./client";
import { notify } from "@/lib/notifications";

const fields = [
  ["current", "Contraseña actual", "Introduce tu contraseña actual", "current-password"],
  ["next", "Nueva contraseña", "Mínimo 12 caracteres", "new-password"],
  ["confirm", "Confirmar contraseña", "Repite la contraseña", "new-password"],
] as const;

export function PasswordPanel() {
  const [shown, setShown] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);
  const router = useRouter();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending.current) return;
    setError("");
    const form = event.currentTarget;
    const data = new FormData(form);
    const currentPassword = String(data.get("current") ?? "");
    const newPassword = String(data.get("next") ?? "");
    if (newPassword !== data.get("confirm")) { setError("Las contraseñas nuevas no coinciden."); return; }
    if (newPassword === currentPassword) { setError("La nueva contraseña debe ser diferente."); return; }
    pending.current = true; setBusy(true);
    try {
      await accountMutation("password", "POST", JSON.stringify({ currentPassword, newPassword }));
      form.reset();
      notify.success("Contraseña actualizada. Inicia sesión con tu nueva contraseña.", { id: "account:password" });
      router.replace("/"); router.refresh();
    } catch (error) {
      setError(error instanceof Error ? error.message : "No se pudo actualizar la contraseña.");
    } finally { pending.current = false; setBusy(false); }
  }

  return <section className="ka-card ka-security" aria-labelledby="ka-password-heading">
    <h2 id="ka-password-heading">Cambiar contraseña</h2>
    <p>Usa una contraseña de al menos 12 caracteres. Después de guardarla, vuelve a iniciar sesión.</p>
    <form onSubmit={submit} aria-busy={busy} aria-describedby={error ? "ka-password-error" : undefined}>
      {fields.map(([id, label, placeholder, complete]) => {
        const visible = shown.includes(id);
        return <div className="ka-password-field" key={id}>
          <label htmlFor={`ka-password-${id}`}>{label}</label>
          <div>
            <input id={`ka-password-${id}`} name={id} type={visible ? "text" : "password"} placeholder={placeholder} required
              minLength={id === "current" ? 1 : 12} maxLength={128} autoComplete={complete} disabled={busy} />
            <button type="button" aria-label={`${visible ? "Ocultar" : "Mostrar"} ${label.toLowerCase()}`} aria-pressed={visible}
              onClick={() => setShown(values => visible ? values.filter(value => value !== id) : [...values, id])}>
              {visible ? <EyeOff size={15} /> : <Eye size={15} />}
            </button>
          </div>
        </div>;
      })}
      {error && <p id="ka-password-error" className="ka-error" role="alert">{error}</p>}
      <button type="submit" className="ka-button ka-primary ka-pill" disabled={busy}>{busy && <LoaderCircle size={14} className="animate-spin" />}{busy ? "Guardando…" : "Guardar Cambios"}</button>
    </form>
  </section>;
}
