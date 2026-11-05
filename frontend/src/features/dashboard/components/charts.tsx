"use client";
import { ResponsiveContainer, LineChart, Line, CartesianGrid, XAxis, YAxis, Tooltip, BarChart, Bar } from "recharts";
import type { Breakdown, DashboardData } from "../types";
import { formatMoney, formatCount } from "../filters";
import { NoData } from "./panel";
const axis = { fontSize: 11, fill: "var(--admin-muted)" };
export function SalesChart({ data }: { data: DashboardData["sales"] }) {
  if (!data.length) return <div className="dash-sales-empty"><div className="dash-axis-label">Importe (S/)</div><div className="dash-grid-lines" aria-hidden="true" /><NoData>Aún no hay información de ventas disponible para este período.</NoData><span className="dash-axis-date">Fecha</span></div>;
  return <div className="dash-chart"><ResponsiveContainer width="100%" height="100%" minWidth={0}><LineChart data={data} accessibilityLayer margin={{ top: 16, right: 16, left: 4, bottom: 12 }}>
    <CartesianGrid stroke="var(--admin-border)" strokeDasharray="3 4" vertical={false} /><XAxis dataKey="date" tick={axis} tickLine={false} axisLine={false} /><YAxis tick={axis} tickFormatter={formatMoney} tickLine={false} axisLine={false} width={95} /><Tooltip formatter={value => formatMoney(Number(value))} /><Line name="Ventas" dataKey="amount" stroke="#2563eb" strokeWidth={2.5} dot={{ r: 3 }} isAnimationActive={false} />
  </LineChart></ResponsiveContainer></div>;
}
export function BreakdownChart({ data, money = false, empty }: { data: Breakdown[]; money?: boolean; empty: string }) {
  if (!data.length || !data.some(item => item.value > 0)) return <NoData>{empty}</NoData>;
  const format = money ? formatMoney : formatCount;
  return <div className="dash-chart"><ResponsiveContainer width="100%" height="100%" minWidth={0}><BarChart data={data} layout="vertical" accessibilityLayer margin={{ right: 24, left: 0, bottom: 12 }}>
    <CartesianGrid stroke="var(--admin-border)" strokeDasharray="3 4" horizontal={false} /><XAxis type="number" tick={axis} tickFormatter={format} axisLine={false} tickLine={false} /><YAxis dataKey="label" type="category" width={105} tick={axis} axisLine={false} tickLine={false} /><Tooltip formatter={value => format(Number(value))} /><Bar dataKey="value" name={money ? "Importe" : "Cantidad"} fill="#2563eb" radius={[0, 10, 10, 0]} barSize={20} isAnimationActive={false} />
  </BarChart></ResponsiveContainer></div>;
}
