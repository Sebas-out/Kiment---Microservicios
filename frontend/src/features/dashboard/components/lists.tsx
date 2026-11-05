import type { CriticalStock, ProductSales } from "../types";
import { formatMoney, formatCount } from "../filters";
import { NoData } from "./panel";
export function TopProducts({ data }: { data: ProductSales[] }) {
  if (!data.length) return <NoData>Los productos más vendidos aparecerán cuando se habilite Ventas POS.</NoData>;
  return <div className="dash-table-wrap"><table><caption className="sr-only">Productos más vendidos</caption><thead><tr><th>Producto / variante</th><th>Cantidad</th><th>Importe</th></tr></thead><tbody>{data.map(item => <tr key={item.id}><td><strong>{item.name}</strong>{item.variant && <small>{item.variant}</small>}</td><td>{formatCount(item.quantity)}</td><td>{formatMoney(item.amount)}</td></tr>)}</tbody></table></div>;
}
export function StockList({ data }: { data: CriticalStock[] }) {
  if (!data.length) return <NoData>Sin información de stock disponible.</NoData>;
  return <div className="dash-stock-columns">{(["out", "low"] as const).map(level => <section key={level}><h4>{level === "out" ? "Agotados" : "Prontos a agotarse"}</h4><ul>{data.filter(item => item.level === level).map(item => <li key={item.id}><div><strong>{item.name}</strong>{item.variant && <small>{item.variant}</small>}</div><span className={`dash-stock-badge ${level}`}>Stock {formatCount(item.available)}</span></li>)}</ul>{!data.some(item => item.level === level) && <p className="dash-stock-none">Sin productos en este nivel.</p>}</section>)}</div>;
}
