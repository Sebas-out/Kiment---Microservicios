"use client";

import { Pagination } from "@/components/admin/pagination";
import { Printer, Check } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";

export type Label = { name: string; sku: string; barcode: string; color: string; size: string; price: string; wholesale: string; stock?: Record<string, number> };

export function ProductDialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { dialog.current?.showModal(); }, []);
  return <dialog ref={dialog} className="kp-report-dialog" aria-label={title} onClose={onClose}>
    <header className="kp-between"><h2>{title}</h2><button type="button" aria-label="Cerrar" onClick={() => dialog.current?.close()}>×</button></header>
    {children}
  </dialog>;
}

const left = ["0001101","0011001","0010011","0111101","0100011","0110001","0101111","0111011","0110111","0001011"];
const parity = ["LLLLLL","LLGLGG","LLGGLG","LLGGGL","LGLLGG","LGGLLG","LGGGLL","LGLGLG","LGLGGL","LGGLGL"];
const invert = (pattern: string) => pattern.replace(/[01]/g, bit => bit === "1" ? "0" : "1");

function ean13(code: string) {
  if (!/^\d{13}$/.test(code)) return null;
  const digits = [...code].map(Number);
  if (digits.reduce((sum, digit, index) => sum + digit * (index % 2 ? 3 : 1), 0) % 10) return null;
  const first = digits.slice(1, 7).map((digit, index) => parity[digits[0]][index] === "L" ? left[digit] : [...invert(left[digit])].reverse().join("")).join("");
  const last = digits.slice(7).map(digit => invert(left[digit])).join("");
  return "101" + first + "01010" + last + "101";
}

export function BarcodeDialog({ rows, onClose }: { rows: Label[]; onClose: () => void }) {
  const [page, setPage] = useState(1);
  const [format, setFormat] = useState("a4");
  const [printSelection, setPrintSelection] = useState(false);
  const [query, setQuery] = useState("");
  const [selecting, setSelecting] = useState(false);
  const [counts, setCounts] = useState<Record<string, number>>({});
  const shown = rows.filter(row => row.barcode && [row.name, row.sku, row.barcode].some(value => value.toLowerCase().includes(query.trim().toLowerCase())));
  const printable = shown.filter(row => ean13(row.barcode));
  const pages = Math.max(1, Math.ceil(shown.length / 10));
  const currentPage = Math.min(page, pages);
  const selectedCount = printable.reduce((sum, row) => sum + (counts[row.barcode] ?? 0), 0);
  function changeCount(code: string, quantity: number) {
    setCounts(previous => ({ ...previous, [code]: Math.max(0, Math.min(100, Math.trunc(quantity) || 0)) }));
  }
  function print(selection: boolean) {
    setPrintSelection(selection);
    requestAnimationFrame(() => requestAnimationFrame(() => window.print()));
  }
  function label(row: Label) {
    const bits = ean13(row.barcode);
    return <div className="kp-barcode-label">
      <span className="kp-label-title">{row.name} - {row.color} / {row.size}</span><strong>SKU: {row.sku}</strong>
      {bits ? <svg viewBox="0 0 117 44" role="img" aria-label={row.barcode}>{[...bits].map((bit, index) => bit === "1" && <rect key={index} x={index + 11} width="1" height="44" />)}</svg> : <span>El código no es un EAN-13 válido.</span>}
      <span>{row.barcode}</span>
    </div>;
  }
  return <ProductDialog title="Códigos de barras de productos" onClose={onClose}>
    <style media="print">{format === "ticket" ? "@page { size:80mm auto; margin:4mm; }" : format === "letter" ? "@page { size:letter; margin:12mm; }" : "@page { size:A4; margin:12mm; }"}</style>
    <div className="kp-report-controls">
      <p className="kp-muted">{shown.length} variantes con código de barras</p>
      <input aria-label="Buscar código de barras" placeholder="Buscar por nombre, SKU o código de barras…" value={query} onChange={event => { setQuery(event.target.value); setPage(1); }} />
      <div className="kp-chips kp-barcode-toolbar">
        <button type="button" className="kp-print-all" disabled={!printable.length} onClick={() => print(false)}><Printer size={15} />Imprimir todos</button>
        <button type="button" aria-pressed={selecting} onClick={() => setSelecting(!selecting)}><Check size={15} />Seleccionar cantidades</button>
        {selecting && <button className="kp-print-selected" type="button" disabled={!selectedCount} onClick={() => print(true)}><Printer size={15} />Imprimir {selectedCount} etiquetas</button>}
        <label className="kp-print-format">Formato
          <select aria-label="Formato de impresión" value={format} onChange={event => setFormat(event.target.value)}>
            <option value="a4">A4 (Genérico)</option><option value="letter">Carta (Genérico)</option><option value="ticket">Ticket 80mm</option>
          </select>
        </label>
      </div>
    </div>
    {!shown.length && <p className="kp-muted">No hay códigos que coincidan con la búsqueda.</p>}
    <div className="kp-label-grid">{shown.slice((currentPage - 1) * 10, currentPage * 10).map(row => <div className="kp-card kp-label-card" data-selected={selecting && (counts[row.barcode] ?? 0) > 0} key={row.barcode}>
      {selecting && ean13(row.barcode) && <button className="kp-label-select" aria-label={"Seleccionar " + row.sku} aria-pressed={(counts[row.barcode] ?? 0) > 0} onClick={() => changeCount(row.barcode, counts[row.barcode] ? 0 : 1)}><Check size={13} /></button>}
      {label(row)}
      {selecting && ean13(row.barcode) && <div className="kp-label-quantity">
        <button type="button" aria-label={"Reducir " + row.sku} disabled={!counts[row.barcode]} onClick={() => changeCount(row.barcode, (counts[row.barcode] ?? 0) - 1)}>−</button>
        <input aria-label={"Cantidad para " + row.sku} type="number" min="0" max="100" value={counts[row.barcode] ?? 0} onChange={event => changeCount(row.barcode, Number(event.target.value))} />
        <button type="button" aria-label={"Aumentar " + row.sku} onClick={() => changeCount(row.barcode, (counts[row.barcode] ?? 0) + 1)}>+</button>
      </div>}
    </div>)}</div>
    <Pagination total={shown.length} label="códigos" page={currentPage} pages={pages} onChange={setPage} />
    <div className={"kp-label-print" + (format === "ticket" ? " kp-print-ticket" : "")}>{printable.flatMap(row => Array.from({ length: printSelection ? counts[row.barcode] ?? 0 : 1 }, (_, index) => <div key={row.barcode + ":" + index}>{label(row)}</div>))}</div>
  </ProductDialog>;
}

export function exportProducts(rows: Label[], branches: { id: string; name: string }[]) {
  const values = [["Producto", "Color", "Talla", "SKU", "Código de barras", "Precio", "Precio mayorista", ...branches.map(branch => branch.name)],
    ...rows.map(row => [row.name, row.color, row.size, row.sku, row.barcode, row.price, row.wholesale, ...branches.map(branch => String(row.stock?.[branch.id] ?? 0))])];
  const escape = (value: string) => value.replace(/[&<>]/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" })[char]!);
  const table = values.map(row => "<Row>" + row.map(value => '<Cell><Data ss:Type="String">' + escape(value) + "</Data></Cell>").join("") + "</Row>").join("");
  const workbook = '<?xml version="1.0" encoding="UTF-8"?><?mso-application progid="Excel.Sheet"?><Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"><Worksheet ss:Name="Productos"><Table>' + table + "</Table></Worksheet></Workbook>";
  const url = URL.createObjectURL(new Blob([workbook], { type: "application/xml" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = "reporte-productos.xml";
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function AnalyticsDialog({ rows, branches, products, onClose }: { rows: Label[]; branches: { id: string; name: string }[]; products: number; onClose: () => void }) {
  return <ProductDialog title="Reporte analítico" onClose={onClose}>
    <dl className="kp-report-totals">
      <div><dt>Productos</dt><dd>{products}</dd></div>
      <div><dt>Variantes</dt><dd>{rows.length}</dd></div>
      <div><dt>Variantes con stock</dt><dd>{rows.filter(row => branches.some(branch => (row.stock?.[branch.id] ?? 0) > 0)).length}</dd></div>
    </dl>
    <h3>Unidades por sucursal</h3>
    <ul>{branches.map(branch => <li key={branch.id}>{branch.name} · {rows.reduce((sum, row) => sum + (row.stock?.[branch.id] ?? 0), 0)} und.</li>)}</ul>
    {!branches.length && <p className="kp-muted">Sin sucursales disponibles.</p>}
  </ProductDialog>;
}
