import { Banknote, ShoppingBag, CreditCard, Package } from "lucide-react";
import type { DashboardData } from "../types";
import { formatMoney, formatCount } from "../filters";
export function Metrics({ totals, loading }: { totals: DashboardData["totals"]; loading: boolean }) {
  const items = [
    { label: "Ventas totales", value: totals.sales, icon: Banknote, money: true, color: "blue" },
    { label: "Número de ventas", value: totals.count, icon: ShoppingBag, money: false, color: "cyan" },
    { label: "Ticket promedio", value: totals.average, icon: CreditCard, money: true, color: "green" },
    { label: "Productos vendidos", value: totals.products, icon: Package, money: false, color: "violet" },
  ];
  return <div className="dash-metrics">{items.map(({ label, value, icon: Icon, money, color }) => <section className="admin-card dash-metric" key={label} aria-label={label}>
    <div><h3>{label}</h3><span className={`dash-metric-icon ${color}`}><Icon size={19} strokeWidth={1.5} aria-hidden="true" /></span></div>
    <p>{loading ? <span className="dash-skeleton" aria-label="Cargando" /> : value === null ? <span className="dash-missing">Sin datos</span> : money ? formatMoney(value) : formatCount(value)}</p>
  </section>)}</div>;
}
