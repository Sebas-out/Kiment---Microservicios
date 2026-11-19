"use client";

import { Pagination } from "@/components/admin/pagination";

import { useEffect, useRef, useState } from "react";
import { ArrowDownToLine, ArrowUpFromLine, ArrowLeft, ArrowRight, ArrowLeftRight, Building2, Package, Plus, Search, Trash2, ScanLine } from "lucide-react";
import { useBranchOptions } from "@/features/branches/use-branch-options";
import "./inventory.css";

type Variant = { color: string; size: string; sku: string; barcode: string; stock?: Record<string, number>; price: string };
type Product = { id: string; name: string; image: string; colorImages?: Record<string, string[]>; variants: Variant[] };
type Item = { product: Product; variant: Variant; quantity: number };
type Event = { id: string; date: string; type: string; product: string; detail: string; branch: string; destination?: string; reason: string; before: number; after: number; quantity: number; user: string };

export function Inventory({ transfer, account, user, editable }: { transfer: boolean; account: string; user: string; editable: boolean }) {
  const key = "kiments:product-drafts:" + account;
  const stateKey = key + ":inventory";
  const branches = useBranchOptions(true);
  const [products, setProducts] = useState<Product[]>([]);
  const [events, setEvents] = useState<Event[]>([]);
  const [editing, setEditing] = useState(false);
  const [outgoing, setOutgoing] = useState(false);
  const [origin, setOrigin] = useState("");
  const [destination, setDestination] = useState("");
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState<Item[]>([]);
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const searchRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    const timer = setTimeout(() => {
      try {
        const stored = JSON.parse(localStorage.getItem(stateKey) ?? "null") as { products: Product[]; events: Event[] } | null;
        setProducts(JSON.parse(localStorage.getItem(key) ?? "[]"));
        setEvents(stored?.events ?? []);
      } catch { setError("No se pudo leer el inventario local."); }
    }, 0);
    return () => clearTimeout(timer);
  }, [key, stateKey]);
  const list = events.filter(event => transfer ? event.type === "Traspaso" : true);
  const pages = Math.max(1, Math.ceil(list.length / 10));
  const current = Math.min(page, pages);
  const source = branches.items?.find(branch => branch.id === origin);
  const target = branches.items?.find(branch => branch.id === destination);
  const quantity = selected.reduce((sum, item) => sum + item.quantity, 0);
  const invalid = !origin || (transfer && (!destination || destination === origin)) || !selected.length ||
    selected.some(item => !Number.isSafeInteger(item.quantity) || item.quantity <= 0 || ((transfer || outgoing) && item.quantity > (item.variant.stock?.[origin] ?? 0)));
  function reset() { setEditing(false); setSelected([]); setQuery(""); setReason(""); setError(""); }
  function pick(product: Product, variant: Variant) {
    if (!selected.some(item => item.product.id === product.id && item.variant.sku === variant.sku)) {
      setSelected(transfer ? [...selected, { product, variant, quantity: 1 }] : [{ product, variant, quantity: 1 }]);
    }
    setQuery("");
  }
  function changeQuantity(index: number, value: number) {
    setSelected(selected.map((item, position) => position === index ? { ...item, quantity: Math.max(0, value) } : item));
  }
  function commit() {
    if (!editable || invalid) return;
    try {
      const latest = JSON.parse(localStorage.getItem(key) ?? "[]") as Product[];
      const nextEvents: Event[] = [];
      const updated = latest.map(product => ({ ...product, variants: product.variants.map(variant => {
        const item = selected.find(row => row.product.id === product.id && row.variant.sku === variant.sku);
        if (!item) return variant;
        const before = variant.stock?.[origin] ?? 0;
        const delta = outgoing || transfer ? -item.quantity : item.quantity;
        if (before + delta < 0) throw new Error("El stock cambió. Revisa las cantidades.");
        const stock = { ...variant.stock, [origin]: before + delta };
        if (transfer) stock[destination] = (stock[destination] ?? 0) + item.quantity;
        nextEvents.push({ id: crypto.randomUUID(), date: new Date().toISOString(), type: transfer ? "Traspaso" : outgoing ? "Salida" : "Entrada", product: product.name, detail: variant.color + " / " + variant.size + " — " + variant.sku, branch: source?.name ?? "", destination: target?.name, reason, before, after: before + delta, quantity: item.quantity, user });
        return { ...variant, stock };
      }) }));
      const history = JSON.parse(localStorage.getItem(stateKey) ?? "null") as { events: Event[] } | null;
      const next = [...nextEvents, ...(history?.events ?? [])];
      // Keep a recoverable snapshot if the second storage write fails.
      localStorage.setItem(stateKey, JSON.stringify({ products: latest, events: history?.events ?? [] }));
      localStorage.setItem(key, JSON.stringify(updated));
      localStorage.setItem(stateKey, JSON.stringify({ products: updated, events: next }));
      setProducts(updated);
      setEvents(next);
      setPage(1);
      reset();
    } catch (failure) { setError(failure instanceof Error ? failure.message : "No se pudo registrar el borrador."); }
  }
  function branchCards(value: string, choose: (id: string) => void, omit?: string) {
    return <div className="ki-branches">{branches.items?.filter(branch => branch.id !== omit).map(branch =>
      <button type="button" key={branch.id} aria-pressed={value === branch.id} onClick={() => choose(branch.id)}>
        <Building2 size={18} /><span><strong>{branch.name}</strong><small>{branch.city || "Ciudad sin registro"}</small><em data-warehouse={branch.branchType === "ALMACEN"}>{branch.branchType === "ALMACEN" ? "Almacén" : branch.branchType === "MIXTO" ? "Mixto" : "Venta"}</em></span>
      </button>)}</div>;
  }
  function thumbnail(item: Item) {
    const image = item.product.colorImages?.[item.variant.color]?.find(Boolean) || item.product.image;
    // eslint-disable-next-line @next/next/no-img-element
    return image ? <img src={image} alt="" /> : <Package size={24} />;
  }
  return <div className="ki">
    <header className="ki-heading">
      {editing && <button aria-label="Volver al historial" onClick={reset}><ArrowLeft size={16} /></button>}
      <div><h1>{editing ? transfer ? "Nuevo Traspaso" : "Nuevo Movimiento" : transfer ? "Traspasos de Stock" : "Movimientos de Stock"}</h1>
        <p>{editing ? transfer ? "Mueve unidades de inventario entre sucursales." : "Registra una entrada o salida manual de inventario." : transfer ? "Historial de traspasos entre sucursales." : "Historial de entradas, salidas, ajustes y otros movimientos de inventario."}</p></div>
      {!editing && editable && <button className="ki-primary" onClick={() => setEditing(true)}><Plus size={16} />Añadir {transfer ? "traspaso" : "movimiento"}</button>}
      {editing && transfer && <span className="ki-group"><ArrowLeftRight size={14} />Traspaso grupal</span>}
    </header>
    {error && <p role="alert" className="ki-warning">{error}</p>}
    {!editing ? <>
      <div className="ki-table"><table><thead><tr>{(transfer ? ["Fecha", "Ruta", "Producto / Variante", "Cantidad", "Motivo", "Usuario"] : ["Fecha", "Tipo", "Producto / Variante", "Sucursal", "Motivo", "Stock", "Usuario"]).map(label => <th key={label}>{label}</th>)}</tr></thead>
        <tbody>{list.slice((current - 1) * 10, current * 10).map(event => <tr key={event.id}>
          <td>{new Date(event.date).toLocaleString("es-PE")}</td>
          <td>{transfer ? <>{event.branch}<ArrowRight size={12} />{event.destination}</> : <span className={"ki-type " + (event.type === "Entrada" ? "ki-green" : "")}>{event.type}</span>}</td>
          <td><strong>{event.product}</strong><small>{event.detail}</small></td>
          <td>{transfer ? event.quantity : event.branch}</td><td>{event.reason || "—"}</td>
          {!transfer && <td>{event.before} → <strong>{event.after}</strong> <span className={event.after >= event.before ? "ki-positive" : "ki-negative"}>{event.after > event.before ? "+" : ""}{event.after - event.before}</span></td>}
          <td>{event.user}</td>
        </tr>)}</tbody></table>
        {!list.length && <p className="ki-empty">No se encontraron {transfer ? "traspasos" : "movimientos"}</p>}
      </div>
      <Pagination total={list.length} label={transfer ? "traspasos" : "movimientos"} page={current} pages={pages} onChange={setPage} />
    </> : <div className="ki-layout"><div className="ki-main">
      {!transfer && <section><h2>Tipo de movimiento</h2><div className="ki-types">{[false, true].map(value => <button key={String(value)} className={!value ? "ki-entry" : "ki-exit"} aria-pressed={outgoing === value} onClick={() => setOutgoing(value)}>{value ? <ArrowUpFromLine /> : <ArrowDownToLine />}<strong>{value ? "Salida" : "Entrada"}</strong><small>{value ? "Descuenta stock" : "Suma stock"}</small></button>)}</div></section>}
      <section><h2>{transfer ? "Sucursal de origen" : "Sucursal"}</h2><p>{transfer ? "Desde donde se retirará el stock." : "Selecciona la sucursal donde se registrará el movimiento."}</p>
        {branches.loading ? <p>Cargando sucursales…</p> : branches.error ? <p role="alert">{branches.error}<button onClick={branches.retry}>Reintentar</button></p> : branchCards(origin, id => { setOrigin(id); setDestination(""); setSelected([]); })}</section>
      {transfer && <section><h2>Sucursal de destino</h2><p>Hacia donde se enviará el stock.</p>{origin ? branchCards(destination, setDestination, origin) : <p className="ki-warning">Primero selecciona una sucursal de origen.</p>}</section>}
      <section><h2>{transfer ? "Productos a traspasar" : "Producto / Variante"}</h2><p>Busca por nombre de producto, SKU o código de barras.</p>
        {!origin ? <p className="ki-warning">Selecciona una sucursal para buscar productos.</p> : <>
          <div className="ki-search"><Search size={16} /><input ref={searchRef} aria-label="Buscar producto o variante" placeholder="Buscar por nombre, SKU o código de barras…" value={query} onChange={event => setQuery(event.target.value)} /><button onClick={() => searchRef.current?.focus()} title="Enfoca la búsqueda para ingresar el código con tu lector"><ScanLine size={15} />Escanear</button></div>
          {query.trim() && <div className="ki-results">{products.flatMap(product => product.variants.filter(variant => [product.name, variant.sku, variant.barcode].some(value => value.toLowerCase().includes(query.toLowerCase()))).map(variant =>
            <button key={product.id + variant.sku} onClick={() => pick(product, variant)}>{thumbnail({ product, variant, quantity: 1 })}<span><strong>{product.name}</strong><small>{variant.color} / {variant.size} — {variant.sku}</small></span><span>{variant.stock?.[origin] ?? 0}<small>en stock</small></span></button>))}{!products.length && <p>No hay productos locales. Crea un borrador en Productos.</p>}</div>}
          {selected.length > 0 && <p>Productos agregados ({selected.length}) <small>Total {quantity} und.</small></p>}
          {selected.map((item, index) => <div className="ki-selected" key={item.product.id + item.variant.sku}>{thumbnail(item)}<span><strong>{item.product.name}</strong><small>{item.variant.color} / {item.variant.size} — {item.variant.sku}</small><small>Stock origen: {item.variant.stock?.[origin] ?? 0}</small></span>
            {transfer && <div className="ki-stepper"><button aria-label={"Reducir " + item.variant.sku} onClick={() => changeQuantity(index, item.quantity - 1)}>−</button><input aria-label={"Cantidad " + item.variant.sku} type="number" min="1" step="1" value={item.quantity} onChange={event => changeQuantity(index, Number(event.target.value))} /><button aria-label={"Aumentar " + item.variant.sku} onClick={() => changeQuantity(index, item.quantity + 1)}>+</button></div>}
            <button aria-label={"Quitar " + item.variant.sku} onClick={() => setSelected(selected.filter((_, position) => position !== index))}><Trash2 size={15} /></button></div>)}
        </>}
        {transfer && <label>Motivo (opcional)<input maxLength={255} value={reason} onChange={event => setReason(event.target.value)} /><small>{reason.length}/255</small></label>}
      </section>
      {!transfer && <section><h2>Detalles del movimiento</h2><div className="ki-details"><label>Cantidad *
        <div className="ki-stepper"><button disabled={!selected.length} onClick={() => changeQuantity(0, (selected[0]?.quantity ?? 0) - 1)}>−</button><input aria-label="Cantidad" type="number" min="1" step="1" disabled={!selected.length} value={selected[0]?.quantity ?? 0} onChange={event => changeQuantity(0, Number(event.target.value))} /><button disabled={!selected.length} onClick={() => changeQuantity(0, (selected[0]?.quantity ?? 0) + 1)}>+</button></div></label>
        <label>Motivo (opcional)<input placeholder="Ej. Ingreso por compra" maxLength={150} value={reason} onChange={event => setReason(event.target.value)} /><small>{reason.length}/150</small></label></div></section>}
      {invalid && selected.length > 0 && <p className="ki-warning">Selecciona las sucursales y una cantidad válida. Las salidas y traspasos requieren stock suficiente.</p>}
      <div className="ki-actions"><button onClick={reset}>Cancelar</button><button className={transfer ? "ki-primary" : "ki-save"} disabled={invalid || !editable} onClick={commit}>{transfer ? <ArrowLeftRight size={15} /> : <ArrowDownToLine size={15} />}Registrar {transfer ? "traspaso (" + quantity + ")" : outgoing ? "Salida" : "Entrada"}</button></div>
    </div><aside className="ki-sidebar" aria-label={transfer ? "Resumen del traspaso" : "Resumen del movimiento"}>
      {transfer && <section className="ki-route-card"><h2>Ruta del traspaso</h2>
        <div className="ki-route">
          <span data-selected={!!source}><strong>{source?.name || "Origen"}</strong>{source && <small>{source.branchType === "ALMACEN" ? "Almacén" : source.branchType === "MIXTO" ? "Mixto" : "Venta"}</small>}</span>
          <ArrowRight size={18} />
          <span data-selected={!!target}><strong>{target?.name || "Destino"}</strong>{target && <small>{target.branchType === "ALMACEN" ? "Almacén" : target.branchType === "MIXTO" ? "Mixto" : "Venta"}</small>}</span>
        </div>
      </section>}
      {!selected.length ? <div className="ki-placeholder"><Package /><p>{transfer ? "Sin productos agregados" : "Selecciona un producto"}</p><small>{transfer ? "Busca y agrega productos al traspaso" : "Aquí verás el impacto del movimiento en el stock"}</small></div> : transfer ?
        <section className="ki-sidebar-products">
          <header><h2>Productos ({selected.length})</h2><span className="ki-type">{quantity} und.</span></header>
          {selected.map((item, index) => <div className="ki-sidebar-product" key={item.product.id + item.variant.sku}>
            <div className="ki-product-summary">{thumbnail(item)}<span><strong>{item.product.name}</strong><small>{item.variant.color} / {item.variant.size} — {item.variant.sku}</small><small>Stock: {item.variant.stock?.[origin] ?? 0}</small></span>
              <button type="button" aria-label={"Quitar del resumen " + item.variant.sku} onClick={() => setSelected(selected.filter((_, position) => position !== index))}><Trash2 size={14} /></button>
            </div>
            <div className="ki-sidebar-quantity"><small>Cant.</small><div className="ki-stepper">
              <button aria-label={"Reducir en resumen " + item.variant.sku} disabled={item.quantity <= 1} onClick={() => changeQuantity(index, item.quantity - 1)}>−</button>
              <input aria-label={"Cantidad en resumen " + item.variant.sku} type="number" min="1" step="1" value={item.quantity} onChange={event => changeQuantity(index, Number(event.target.value))} />
              <button aria-label={"Aumentar en resumen " + item.variant.sku} onClick={() => changeQuantity(index, item.quantity + 1)}>+</button>
            </div></div>
          </div>)}
        </section> : selected.map(item => <div className="ki-movement-preview" key={item.product.id + item.variant.sku}>
          <section>
            <div className="ki-product-summary">{thumbnail(item)}<span><strong>{item.product.name}</strong><small>{item.variant.color} / {item.variant.size}</small></span></div>
            <div className="ki-preview-fields"><div><small>SKU</small><span>{item.variant.sku}</span></div><div><small>Precio</small><span>S/ {Number(item.variant.price).toFixed(2)}</span></div></div>
            <small>{item.variant.color} · {item.variant.size}</small>
          </section>
          <div className={"ki-impact" + (outgoing ? " ki-impact-outgoing" : "")}>
            <h2>Impacto en stock</h2>
            <div><span><small>Actual</small><strong>{item.variant.stock?.[origin] ?? 0}</strong></span>
              <b>{item.quantity > 0 ? (outgoing ? "−" : "+") + item.quantity : "—"}</b>
              <span><small>Final</small><strong>{item.quantity > 0 ? (item.variant.stock?.[origin] ?? 0) + (outgoing ? -item.quantity : item.quantity) : "—"}</strong></span>
            </div>
          </div>
        </div>)}
      <section className="ki-summary-card"><h2>Resumen</h2>
        <dl aria-live="polite">
          {transfer ? <>
            <div><dt>Origen</dt><dd>{source?.name || "Por seleccionar"}</dd></div>
            <div><dt>Destino</dt><dd>{target?.name || "Por seleccionar"}</dd></div>
            <div><dt>Productos</dt><dd>{selected.length}</dd></div>
            <div><dt>Total unidades</dt><dd>{quantity}</dd></div>
          </> : <>
            <div><dt>Tipo</dt><dd className={outgoing ? "ki-negative" : "ki-positive"}>{outgoing ? "Salida" : "Entrada"}</dd></div>
            <div><dt>Sucursal</dt><dd>{source?.name || "Por seleccionar"}</dd></div>
            <div><dt>Producto</dt><dd>{selected[0]?.product.name || "Por seleccionar"}</dd></div>
            <div><dt>Variante</dt><dd>{selected[0] ? selected[0].variant.color + " / " + selected[0].variant.size : "Por seleccionar"}</dd></div>
          </>}
          {reason && <div><dt>Motivo</dt><dd>{reason}</dd></div>}
        </dl>
      </section>
    </aside></div>}
    <small className="ki-local">Inventario de prueba · Guardado en este navegador</small>
  </div>;
}
