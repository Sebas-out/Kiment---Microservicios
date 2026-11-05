"use client";
import { useEffect, useState } from "react";
import type { DashboardFilters, DashboardResult, DatePreset } from "../types";
import { presetRange, validRange } from "../filters";
import { loadDashboard } from "../adapter";
import { Filters } from "./filters";
import { Metrics } from "./metrics";
import { Panel, NoData } from "./panel";
import { SalesChart, BreakdownChart } from "./charts";
import { StockList, TopProducts } from "./lists";
import "../dashboard.css";
export function Dashboard({ today, admin }: { today: string; admin: boolean }) {
  const [filters, setFilters] = useState<DashboardFilters>({ preset: "today", ...presetRange("today", today), branchId: null });
  const [result, setResult] = useState<DashboardResult | null>(null);
  const [error, setError] = useState(false);
  const [loading, setLoading] = useState(true);
  const invalid = !validRange(filters.from, filters.to) || filters.to > today;
  useEffect(() => {
    if (invalid) return;
    const controller = new AbortController();
    loadDashboard(filters, controller.signal).then(data => { if (!controller.signal.aborted) { setResult(data); setError(false); setLoading(false); } })
      .catch(() => { if (!controller.signal.aborted) { setError(true); setLoading(false); } });
    return () => controller.abort();
  }, [filters, invalid]);
  function choose(preset: DatePreset) {
    if (preset !== "custom") setLoading(true);
    setFilters(current => ({ ...current, preset, ...(preset === "custom" ? {} : presetRange(preset, today)) }));
  }
  const data = invalid || loading ? undefined : result?.data;
  return <div className="dashboard-4b">
    <div className="admin-page-heading"><div><h2>Dashboard</h2><p>Resumen comercial de ventas, pagos e inventario.</p></div></div>
    <Filters value={filters} today={today} admin={admin} onPreset={choose} onDate={(key, value) => { setLoading(false); setFilters(current => ({ ...current, preset: "custom", [key]: value })); }} invalid={invalid} />
    <p className="dash-availability" role="status">{invalid ? "El rango seleccionado no es válido." : error ? "No se pudo cargar el resumen. Inténtalo nuevamente más tarde." : loading ? "Preparando resumen…" : "Los reportes estarán disponibles cuando se habiliten Ventas POS e Inventario. Las fechas seleccionadas aún no consultan información comercial."}</p>
    <Metrics totals={data?.totals ?? { sales: null, count: null, average: null, products: null }} loading={loading} />
    {error ? <section className="admin-card dash-load-error"><NoData>No se pudo cargar la información del dashboard.</NoData></section> : <>
      <div className="dash-primary-grid"><Panel title="Tendencia de ventas" description="Evolución del importe vendido en el período seleccionado.">{loading ? <NoData>Preparando gráfico…</NoData> : <SalesChart data={data?.sales ?? []} />}</Panel>
      <Panel title="Ingresos por método de pago" description="Distribución de los ingresos según el medio de pago."><BreakdownChart data={data?.payments ?? []} money empty="Sin información de pagos disponible. Efectivo, Yape, Plin y tarjetas se mostrarán cuando existan datos." /></Panel></div>
      <div className="dash-secondary-grid"><Panel title="Productos más vendidos" description="Productos y variantes con mayor salida en el período."><TopProducts data={data?.products ?? []} /></Panel>
      <Panel title="Comprobantes por tipo" description="Distribución según los tipos de documento disponibles."><BreakdownChart data={data?.documents ?? []} empty="Sin información de comprobantes disponible. Los tipos aparecerán al habilitar la facturación." /></Panel></div>
      <Panel title="Stock crítico" description="Productos y variantes agotados o próximos a agotarse."><StockList data={data?.stock ?? []} /></Panel>
    </>}
  </div>;
}
