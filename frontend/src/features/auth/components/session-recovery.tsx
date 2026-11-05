"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { LoaderCircle, ShieldCheck } from "lucide-react";
import { Brand } from "@/components/brand";
import { notify } from "@/lib/notifications";
import { restoreSession, SessionError } from "../session-client";
export function SessionRecovery() {
  const [pending, setPending] = useState(false);
  const busy = useRef(false);
  const router = useRouter();
  async function restore() {
    if (busy.current) return;
    busy.current = true; setPending(true);
    try { await restoreSession(); router.replace("/dashboard"); router.refresh(); }
    catch (error) {
      if (error instanceof SessionError && [400, 401].includes(error.status)) { router.replace("/?session=expired"); router.refresh(); }
      else notify.error(error instanceof Error ? error.message : "No se pudo renovar la sesión.", { id: "auth:session" });
    } finally { busy.current = false; setPending(false); }
  }
  return <main className="session-recovery"><section aria-labelledby="session-title"><Brand />
    <span className="session-recovery-icon" aria-hidden="true"><ShieldCheck size={28} strokeWidth={1.4} /></span>
    <h1 id="session-title">Continúa con tu sesión</h1>
    <p>Por seguridad, necesitamos renovar tu acceso antes de continuar.</p>
    <button type="button" className="submit-button" onClick={restore} disabled={pending}>{pending && <LoaderCircle size={16} className="animate-spin" />} {pending ? "Renovando acceso…" : "Continuar sesión"}</button>
    <Link href="/?session=expired">Volver al inicio de sesión</Link>
  </section></main>;
}
