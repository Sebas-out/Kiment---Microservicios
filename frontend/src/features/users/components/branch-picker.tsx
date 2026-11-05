"use client";
import Link from "next/link";
import { useBranchOptions } from "@/features/branches/use-branch-options";

export function BranchPicker({ value, onChange, disabled, permissions, error, global = false }: { value: string; onChange: (id: string) => void; disabled: boolean; permissions: string[]; error?: string; global?: boolean }) {
  const branches = useBranchOptions(!global && permissions.includes("BRANCH_READ"));
  if (global) return <div className="ku-global"><strong>Acceso global</strong><span>El administrador no necesita una sucursal asignada.</span></div>;
  const invalidCurrent = value && branches.items && !branches.items.some(b => b.id === value);
  return <div className="ku-branch-picker"><label htmlFor="ku-branch">Sucursal principal <span aria-hidden="true">*</span><select id="ku-branch" value={value} disabled={disabled || branches.loading || !!branches.error} onChange={e => onChange(e.target.value)} aria-required="true" aria-invalid={!!error || !!invalidCurrent} aria-describedby="ku-branch-help"><option value="">{branches.loading ? "Cargando sucursales…" : "Selecciona una sucursal"}</option>{invalidCurrent && <option value={value} disabled>Sucursal actual no disponible</option>}{branches.items?.map(branch => <option key={branch.id} value={branch.id}>{branch.name} · {branch.code}</option>)}</select></label>
    <div id="ku-branch-help">{branches.error ? <p role="alert" className="ku-field-error">{branches.error} {permissions.includes("BRANCH_READ") && <button type="button" className="ku-link" onClick={branches.retry}>Reintentar</button>}</p> : branches.items?.length === 0 ? <p className="ku-field-error">No hay sucursales activas. Registra una sucursal antes de asignar personal.{permissions.includes("BRANCH_READ") && <Link href="/sucursales" className="ku-link">Ir a Sucursales</Link>}</p> : <p className="ku-muted">La sucursal es obligatoria para los roles operativos.</p>}{invalidCurrent && <p className="ku-field-error">Elige una sucursal activa antes de continuar.</p>}{error && <p role="alert" className="ku-field-error">{error}</p>}</div>
  </div>;
}
