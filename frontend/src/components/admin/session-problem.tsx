import Link from "next/link";
import { ShieldX, CloudOff } from "lucide-react";
import { EmptyState } from "./empty-state";
export function SessionProblem({ forbidden = false, embedded = false }: { forbidden?: boolean; embedded?: boolean }) {
  const Container = embedded ? "section" : "main";
  return <Container className="admin-session-problem" data-embedded={embedded}><section>
    <EmptyState icon={forbidden ? ShieldX : CloudOff} title={forbidden ? "Acceso no autorizado" : "No pudimos verificar tu sesión"}>
      {forbidden ? "Tu cuenta no tiene permiso para acceder a esta sección." : "El servicio no está disponible en este momento. Tu sesión no se ha cerrado. Inténtalo nuevamente en unos minutos."}
    </EmptyState>
    <div className="admin-problem-actions"><a className="admin-primary-action" href="/dashboard">Volver a intentar</a><Link className="admin-text-link" href="/">Ir al inicio de sesión</Link></div>
  </section></Container>;
}
