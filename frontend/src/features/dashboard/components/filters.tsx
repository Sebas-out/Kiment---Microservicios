import type { DashboardFilters, DatePreset } from "../types";
export function Filters({ value, today, admin, onPreset, onDate, invalid }: {
  value: DashboardFilters; today: string; admin: boolean; onPreset: (preset: DatePreset) => void;
  onDate: (key: "from" | "to", value: string) => void; invalid: boolean;
}) {
  return <section className="dash-filters" aria-label="Filtros del dashboard">
    <div className="dash-presets" role="group" aria-label="Período">{([["today", "Hoy"], ["yesterday", "Ayer"], ["week", "Últimos 7 días"], ["month", "Últimos 30 días"], ["custom", "Rango personalizado"]] as const).map(([id, label]) => <button type="button" key={id} aria-pressed={value.preset === id} onClick={() => onPreset(id)}>{label}</button>)}</div>
    <div className="dash-filter-fields"><div className="dash-date-fields"><label>Desde<input type="date" aria-invalid={invalid} aria-describedby={invalid ? "date-range-error" : undefined} value={value.from} max={today} onChange={event => onDate("from", event.target.value)} /></label><label>Hasta<input type="date" aria-invalid={invalid} aria-describedby={invalid ? "date-range-error" : undefined} value={value.to} max={today} onChange={event => onDate("to", event.target.value)} /></label></div>
    <label className="dash-branch">Sucursal<select disabled value="" aria-describedby="branch-unavailable"><option value="">{admin ? "Todas las sucursales" : "Mi sucursal"}</option></select><small id="branch-unavailable">Filtro de sucursal aún no disponible.</small></label></div>
    {invalid && <p id="date-range-error" role="alert" className="dash-filter-error">Selecciona un rango válido: la fecha inicial no puede superar la final ni incluir fechas futuras.</p>}
  </section>;
}
