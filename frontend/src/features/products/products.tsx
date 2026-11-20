"use client";

import { Pagination } from "@/components/admin/pagination";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import { createPortal } from "react-dom";
import { Grid2X2, List, Shirt, CheckCircle2, Barcode, Pencil, Trash2, FileSpreadsheet, ChartNoAxesColumn, Store, Warehouse, Package, Palette, Plus, Ruler, Search, X } from "lucide-react";
import { AttributeDrawer, defaultColors, Swatch, type AttributeKind } from "./attribute-drawer";

import { useCatalogBranches } from "./use-catalog-branches";

import { CategoryPicker } from "./category-picker";
import { MediaDrawer, type ColorImages } from "./media-drawer";



import { AnalyticsDialog, BarcodeDialog, ProductDialog, exportProducts } from "./product-reports";

import { allRecords, catalogRequest, mapProduct, imageUrl, type AttributeRecord, type RemoteProduct, type RemoteImage } from "./catalog-api";

type Variant = { id?: string; color: string; size: string; sku: string; barcode: string; price: string; wholesale: string; stock?: Record<string, number> };
type Product = { id: string; name: string; category: string; description: string; image: string; colorImages?: ColorImages; variants: Variant[]; inactiveVariants?: Variant[]; autoSku?: boolean; autoBarcode?: boolean };
const keyOf = (color: string, size: string) => JSON.stringify([color, size]);
const unique = (values: string[]) => [...new Set(values.filter(Boolean))];


export function Products({ accountId, editable, canReadBranches, assignedBranchId, canReport }: { canReport: boolean; assignedBranchId?: string | null; accountId: string; editable: boolean; canReadBranches: boolean }) {
  const [records, setRecords] = useState<Product[] | null>(null);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("");
  const [listingPage, setListingPage] = useState(1);
  const [report, setReport] = useState<"barcode" | "analytics">();
  const [barcodeSku, setBarcodeSku] = useState("");
  const [pendingDelete, setPendingDelete] = useState<{ product: Product; variant?: Variant }>();
  const [mode, setMode] = useState<"global" | "variants">("global");
  const [layout, setLayout] = useState<"grid" | "list">("grid");
  const [colorFilter, setColorFilter] = useState("");
  const [colorPage, setColorPage] = useState(0);
  const [branchId, setBranchId] = useState(assignedBranchId ?? "");
  const [available, setAvailable] = useState(false);
  const branches = useCatalogBranches(canReadBranches, assignedBranchId);
  const [draft, setDraft] = useState<Product>();
  const [picks, setPicks] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const [editorVersion, setEditorVersion] = useState(0);
  const saving = useRef(false);
  const attempt = useRef<{ body: string; key: string } | undefined>(undefined);

  async function reload() {
    setRecords((await allRecords<RemoteProduct>("products?enabled=true")).map(mapProduct));
  }
  useEffect(() => {
    let active = true;
    allRecords<RemoteProduct>("products?enabled=true").then(data => { if (active) setRecords(data.map(mapProduct)); }).catch(error => { if (active) setError(error.message); });
    return () => { active = false; };
  }, [accountId]);

  async function save(product: Product) {
    if (!editable || saving.current) return;
    saving.current = true;
    setError("");
    let persistedId = (records ?? []).some(item => item.id === product.id) ? product.id : undefined;
    try {
      const [categories, colors, sizes] = await Promise.all(["categories", "colors", "sizes"].map(kind => allRecords<AttributeRecord>(kind + "?enabled=true")));
      const categoryId = categories.find(item => item.name === product.category)?.id;
      if (!categoryId) throw new Error("Selecciona una categoría registrada.");
      const remote = persistedId ? await catalogRequest<RemoteProduct>("products/" + persistedId) : undefined;
      const payload = { categoryId, name: product.name, description: product.description, autoSku: product.autoSku, autoBarcode: product.autoBarcode,
        variants: product.variants.map(variant => {
          const colorId = colors.find(item => item.name === variant.color)?.id;
          const sizeId = sizes.find(item => item.name === variant.size)?.id;
          if (!colorId || !sizeId) throw new Error("Selecciona colores y tallas registrados.");
          return { colorId, sizeId, sku: product.autoSku ? null : variant.sku, barcode: product.autoBarcode ? null : variant.barcode, price: Number(variant.price), wholesalePrice: variant.wholesale ? Number(variant.wholesale) : null, initialStock: Object.entries(variant.stock ?? {}).map(([branchId, quantity]) => ({ branchId, quantity })) };
        }) };
      let saved: RemoteProduct;
      if (!remote) {
        const body = JSON.stringify(payload);
        if (attempt.current?.body !== body) attempt.current = { body, key: crypto.randomUUID() };
        saved = await catalogRequest<RemoteProduct>("products", "POST", payload, attempt.current.key);
      } else {
        saved = await catalogRequest<RemoteProduct>("products/" + product.id, "PUT", { categoryId, name: product.name, description: product.description });
        for (const variant of product.variants) {
          const previous = remote.variants.find(row => row.colorName === variant.color && row.sizeName === variant.size);
          const body = payload.variants[product.variants.indexOf(variant)];
          if (previous?.id) await catalogRequest("variants/" + previous.id, "PUT", { price: body.price, wholesalePrice: body.wholesalePrice, barcode: variant.barcode, enabled: true });
          else await catalogRequest("products/" + product.id + "/variants", "POST", body);
        }
        for (const previous of remote.variants.filter(row => row.enabled && !product.variants.some(item => item.color === row.colorName && item.size === row.sizeName))) {
          await catalogRequest("variants/" + previous.id + "/status", "PATCH", { enabled: false });
        }
        saved = await catalogRequest<RemoteProduct>("products/" + product.id);
      }
      persistedId = saved.id;
      // Persist the server identity before media operations so retries cannot create a second product.
      setDraft({ ...product, id: saved.id });
      setRecords(previous => [mapProduct(saved), ...(previous ?? []).filter(item => item.id !== saved.id)]);
      const desired = [{ colorId: null as string | null, values: product.image ? [product.image] : [] }, ...colors.map(color => ({ colorId: color.id, values: product.colorImages?.[color.name] ?? [] }))];
      for (const group of desired) {
        const existing = saved.images.filter(image => image.colorId === group.colorId);
        for (const image of existing.filter(image => !group.values.includes(imageUrl(saved.id, image.id)) && (group.colorId !== null || !group.values.some(value => value?.startsWith("data:"))))) {
          await catalogRequest("products/" + saved.id + "/images/" + image.id, "DELETE");
        }
        const ordered: string[] = [];
        for (const value of group.values) {
          if (!value) continue;
          if (!value.startsWith("data:")) {
            const image = existing.find(image => imageUrl(saved.id, image.id) === value);
            if (image) ordered.push(image.id);
            continue;
          }
          const blob = await (await fetch(value)).blob();
          const form = new FormData();
          form.set("file", blob, "product." + (blob.type.split("/")[1] || "png"));
          const image = await catalogRequest<RemoteImage>("products/" + saved.id + "/images" + (group.colorId ? "?colorId=" + group.colorId : ""), "POST", form);
          ordered.push(image.id);
        }
        if (group.colorId && ordered.length > 1) await catalogRequest("products/" + saved.id + "/images/order?colorId=" + group.colorId, "PUT", { imageIds: ordered });
      }
      await reload();
      setDraft(undefined);
      attempt.current = undefined;
    } catch (error) {
      if (persistedId) {
        try {
          const actual = mapProduct(await catalogRequest<RemoteProduct>("products/" + persistedId));
          setRecords(previous => [actual, ...(previous ?? []).filter(item => item.id !== actual.id)]);
          setDraft(actual);
          setEditorVersion(value => value + 1);
        } catch { /* Keep the draft when the service is unreachable. */ }
      }
      setError((error instanceof Error ? error.message : "No se pudo guardar.") + " Revisa el estado actual del producto antes de reintentar; algunas operaciones pueden haberse guardado.");
    }
    finally { saving.current = false; }
  }
  if (draft) return <Editor key={draft.id + ":" + editorVersion} canReadBranches={canReadBranches} assignedBranchId={assignedBranchId} records={records ?? []} initial={draft} onSave={save} onCancel={() => setDraft(undefined)} saveError={error} />;
  const categories = unique((records ?? []).map(record => record.category));
  const colors = unique((records ?? []).flatMap(record => record.variants.map(variant => variant.color)));
  const palette: { name: string; hex?: string }[] = [];
  const swatch = (name: string) => { const variant = records?.flatMap(item => item.variants).find(item => item.color === name) as (Variant & { colorHex?: string }) | undefined; return (variant?.colorHex ? { name, hex: variant.colorHex } : undefined) ?? palette.find(color => color.name === name) ?? defaultColors.find(color => color.name === name) ?? { name }; };
  const search = query.trim().toLocaleLowerCase();
  const visibleBranches = (branches.items ?? []).filter(branch => !branchId || branch.id === branchId);
  const colorPages = Math.max(1, Math.ceil(colors.length / 10));
  const page = Math.min(colorPage, colorPages - 1);
  const cards = (records ?? []).flatMap(product => {
    if (category && product.category !== category) return [];
    const matchesProduct = [product.name, product.category].some(value => value.toLocaleLowerCase().includes(search));
    const variants = product.variants.filter(variant =>
      (matchesProduct || variant.sku.toLocaleLowerCase().includes(search)) &&
      (!colorFilter || variant.color === colorFilter) &&
      (!available || (branchId ? (variant.stock?.[branchId] ?? 0) > 0 : Object.values(variant.stock ?? {}).some(quantity => quantity > 0))));
    if (mode === "variants") return variants.map(variant => ({ product, variants: [variant] }));
    return variants.length || (!product.variants.length && matchesProduct && !colorFilter && !available) ? [{ product, variants }] : [];
  });

  const pageCount = Math.max(1, Math.ceil(cards.length / 10));
  const currentPage = Math.min(listingPage, pageCount);
  const rows = cards.flatMap(({ product, variants }) => variants.map(variant => ({ ...variant, name: product.name })));

  async function remove() {
    if (!editable || !pendingDelete || saving.current) return;
    saving.current = true;
    try {
      const { product, variant } = pendingDelete;
      await catalogRequest(variant?.id ? "variants/" + variant.id + "/status" : "products/" + product.id + "/status", "PATCH", { enabled: false });
      await reload(); setPendingDelete(undefined);
    } catch (error) { setError(error instanceof Error ? error.message : "No se pudo desactivar."); }
    finally { saving.current = false; }
  }

  function open(product: Product) { setError(""); setDraft(product); }

  return <div className="kp kp-product-listing">
    {error && <p role="alert">{error}</p>}
    <div className="kp-toolbar">
      <label className="kp-search"><Search size={17} /><input aria-label="Buscar productos" placeholder="Buscar por nombre, SKU o categoría…" value={query} onChange={event => { setQuery(event.target.value); setListingPage(1); }} /></label>
    </div>
    <div className="kp-toolbar kp-list-controls">
      <select aria-label="Sucursal" value={branchId} onChange={event => { setBranchId(event.target.value); setListingPage(1); }}>
        <option value="">Todas las sucursales</option>
        {branches.items?.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
      </select>
      <div className="kp-chips">
        <button className="kp-available" type="button" aria-pressed={available} onClick={() => { setAvailable(!available); setListingPage(1); }}><CheckCircle2 size={15} /> Disponibles</button>
        <div className="kp-segment" role="group" aria-label="Agrupación del catálogo">
          <button type="button" aria-pressed={mode === "global"} onClick={() => { setMode("global"); setListingPage(1); }}><Shirt size={16} /> Global</button>
          <button type="button" aria-pressed={mode === "variants"} onClick={() => { setMode("variants"); setListingPage(1); }}><Package size={16} /> Variantes</button>
        </div>
      </div>
    </div>
    <div className="kp-card kp-list-filters">
      <div className="kp-chips" role="group" aria-label="Filtrar por categoría">
        {["", ...categories].map(item => <button key={item} type="button" aria-pressed={item === category} onClick={() => { setCategory(item); setListingPage(1); }}>{item || "Todos"}</button>)}
      </div>
      <div className="kp-chips" role="group" aria-label="Filtrar por color">
        <button type="button" aria-pressed={!colorFilter} onClick={() => { setColorFilter(""); setListingPage(1); }}>Todos</button>
        {colors.slice(page * 10, page * 10 + 10).map(color => <button className="kp-color-pick" key={color} type="button" title={color} aria-label={color} aria-pressed={colorFilter === color} onClick={() => { setColorFilter(color); setListingPage(1); }}><Swatch color={swatch(color)} /></button>)}
        {colorPages > 1 && <>
          <button type="button" aria-label="Colores anteriores" disabled={!page} onClick={() => setColorPage(page - 1)}>‹</button>
          <span className="kp-muted">{page + 1}/{colorPages}</span>
          <button type="button" aria-label="Colores siguientes" disabled={page + 1 >= colorPages} onClick={() => setColorPage(page + 1)}>›</button>
        </>}
      </div>
    </div>
    <div className="kp-toolbar kp-list-controls">
      <div className="kp-segment" role="group" aria-label="Diseño del catálogo">
        <button type="button" aria-label="Tarjetas" aria-pressed={layout === "grid"} onClick={() => setLayout("grid")}><Grid2X2 size={16} /></button>
        <button type="button" aria-label="Lista" aria-pressed={layout === "list"} onClick={() => setLayout("list")}><List size={16} /></button>
      </div>
      <div className="kp-chips kp-product-tools">
        <button className="kp-tool-barcode" type="button" onClick={() => { setBarcodeSku(""); setReport("barcode"); }}><Barcode size={16} /> Código de barras</button>
        {canReport && <button className="kp-tool-excel" type="button" disabled={!rows.length} onClick={() => { void exportProducts(branchId, query, available).catch(error => setError(error.message)); }}><FileSpreadsheet size={16} /> Reporte Excel</button>}
        {canReport && <button className="kp-tool-analytics" type="button" onClick={() => setReport("analytics")}><ChartNoAxesColumn size={16} /> Reporte analítico</button>}
      {editable && <button className="kp-primary" disabled={!records} type="button" onClick={() => open({ id: crypto.randomUUID(), name: "", category: "", description: "", image: "", variants: [] })}><Plus size={17} /> Nuevo producto</button>}
      </div>
    </div>
    {!records ? <p role="status">Cargando productos…</p> : cards.length ? <div className={layout === "list" ? "kp-products kp-products-list" : "kp-products"}>
      {cards.slice((currentPage - 1) * 10, currentPage * 10).map(({ product, variants }) => {
        const individual = mode === "variants";
        const variant = variants.find(item => keyOf(item.color, item.size) === picks[product.id]) ?? variants[0];
        const sizes = variants.filter(item => item.color === variant?.color);
        function select(item: Variant) { setPicks(previous => ({ ...previous, [product.id]: keyOf(item.color, item.size) })); }
        return <article className="kp-card kp-list-product" key={individual && variant ? product.id + keyOf(variant.color, variant.size) : product.id}>
          <div className="kp-list-picture">
            <Picture image={individual ? (variant && product.colorImages?.[variant.color]?.find(Boolean)) || "" : product.image} />
            <span className="kp-local-status">Activo</span><span className="kp-badge">{individual ? variant?.size : product.variants.length + " SKUs"}</span>
          </div>
          <div className="kp-card-body">
            {product.category && <span className="kp-list-category">{product.category}</span>}
            <h2>{product.name}</h2>
            {variant && <>
              {individual && <span className="kp-muted">SKU: {variant.sku}</span>}
              <div className="kp-price">S/ {Number(variant.price).toFixed(2)} {variant.wholesale && <small>Mayor: S/ {Number(variant.wholesale).toFixed(2)}</small>}</div>
              {individual ? <div className="kp-chips kp-muted"><Swatch color={swatch(variant.color)} />{variant.color}<span className="kp-badge">{variant.size}</span></div> : <>
                <div className="kp-chips" role="group" aria-label="Colores">{unique(variants.map(item => item.color)).map(color => <button className="kp-color-pick" key={color} type="button" aria-label={color} title={color} aria-pressed={variant.color === color} onClick={() => {
                  const next = variants.find(item => item.color === color && item.size === variant.size) ?? variants.find(item => item.color === color);
                  if (next) select(next);
                }}><Swatch color={swatch(color)} /></button>)}</div>
                <div className="kp-chips" role="group" aria-label="Tallas">{sizes.map(item => <button key={item.size} type="button" aria-pressed={item.size === variant.size} onClick={() => select(item)}>{item.size}</button>)}</div>
              </>}
              <div className="kp-list-stock"><span>Stock</span>
                {visibleBranches.length ? visibleBranches.map(branch => <div key={branch.id}><span>{branch.name}</span><strong data-low={(variant.stock?.[branch.id] ?? 0) <= 5}>{variant.stock?.[branch.id] ?? 0} und.</strong></div>) : <p className="kp-muted">{branches.loading ? "Cargando sucursales…" : "Sin sucursales disponibles"}</p>}
              </div>
              {individual && variant.barcode && <span className="kp-chips kp-muted"><Barcode size={15} />{variant.barcode}</span>}
            </>}
            <footer>
              {individual && variant?.barcode && <button type="button" aria-label={"Código de barras de " + variant.sku} onClick={() => { setBarcodeSku(variant.sku); setReport("barcode"); }}><Barcode size={14} /></button>}
              {editable && <>
                <button type="button" onClick={() => open(product)}><Pencil size={14} /> Editar</button>
                <button type="button" aria-label={individual ? "Eliminar variante " + variant?.sku : "Eliminar " + product.name} onClick={() => { setError(""); setPendingDelete({ product, variant: individual ? variant : undefined }); }}><Trash2 size={14} /> Eliminar</button>
              </>}
            </footer>
          </div>
        </article>;
      })}
    </div> : <div className="kp-card kp-empty"><Package size={32} /><h2>{records.length ? "Sin resultados" : "Todavía no hay productos"}</h2><p>{records.length ? "Prueba con otros filtros." : "Crea un producto y combina sus colores y tallas."}</p></div>}
    {records && <Pagination total={cards.length} label={mode === "global" ? "productos" : "variantes"} page={currentPage} pages={pageCount} onChange={setListingPage} />}
    <p className="kp-muted">Catálogo</p>
    {report === "barcode" && <BarcodeDialog rows={barcodeSku ? rows.filter(row => row.sku === barcodeSku) : rows} onClose={() => setReport(undefined)} />}
    {report === "analytics" && <AnalyticsDialog branchId={branchId} rows={rows} branches={visibleBranches} products={new Set(cards.map(card => card.product.id)).size} onClose={() => setReport(undefined)} />}
    {pendingDelete && <ProductDialog title={pendingDelete.variant ? "Eliminar variante" : "Eliminar producto"} onClose={() => setPendingDelete(undefined)}>
      <p>¿Eliminar {pendingDelete.variant?.sku ?? pendingDelete.product.name} del catálogo?</p>
      {error && <p role="alert">{error}</p>}
      <div className="kp-report-controls kp-chips">
        <button type="button" onClick={() => setPendingDelete(undefined)}>Cancelar</button>
        <button className="kp-primary" type="button" onClick={remove}>Eliminar</button>
      </div>
    </ProductDialog>}
  </div>;
}

function Picture({ image }: { image: string }) {
  // eslint-disable-next-line @next/next/no-img-element
  return <div className="kp-picture">{image ? <img src={image} alt="Foto del producto" /> : <span className="kp-brand-placeholder">KIMENTS<small>TIENDA DE ROPA</small></span>}</div>;
}

function Editor({ initial, canReadBranches, assignedBranchId, records, onSave, onCancel, saveError }: { assignedBranchId?: string | null; canReadBranches: boolean; records: Product[]; initial: Product; onSave: (product: Product) => Promise<void>; onCancel: () => void; saveError: string }) {
  const [product, setProduct] = useState(initial);
  const [submitting, setSubmitting] = useState(false);
  const [view, setView] = useState<"cards" | "list">("cards");
  const [autoSku, setAutoSku] = useState(initial.autoSku ?? true);
  const [autoBarcode, setAutoBarcode] = useState(initial.autoBarcode ?? true);
  const [stockGroup, setStockGroup] = useState<"sales" | "warehouse">("sales");
  const [bulkBranch, setBulkBranch] = useState("");
  const [bulkQuantity, setBulkQuantity] = useState("");
  const branches = useCatalogBranches(canReadBranches, assignedBranchId);
  const branchItems = branches.items ?? [];
  const visibleBranches = branchItems.filter(branch => stockGroup === "warehouse" ? branch.branchType === "ALMACEN" || branch.branchType === "MIXTO" : branch.branchType !== "ALMACEN");

  const [colors, setColors] = useState(unique(initial.variants.map(item => item.color)));
  const [sizes, setSizes] = useState(unique(initial.variants.map(item => item.size)));
  const [drawer, setDrawer] = useState<AttributeKind>();
  const [error, setError] = useState("");
  const [catalog, setCatalog] = useState<{ categories: AttributeRecord[]; colors: AttributeRecord[]; sizes: AttributeRecord[] }>({ categories: [], colors: [], sizes: [] });
  useEffect(() => {
    let active = true;
    Promise.all(["categories", "colors", "sizes"].map(kind => allRecords<AttributeRecord>(kind + "?enabled=true"))).then(([categories, colors, sizes]) => {
      if (active) setCatalog({ categories, colors, sizes });
    }).catch(error => { if (active) setError(error.message); });
    return () => { active = false; };
  }, []);
  const categories = catalog.categories.map(item => item.name);
  const attributes = { colors: catalog.colors, sizes: catalog.sizes };
  function toggle(kind: AttributeKind, name: string) {
    const set = kind === "colors" ? setColors : setSizes;
    set(current => current.includes(name) ? current.filter(item => item !== name) : [...current, name]);
  }
  async function addCategory(name: string, description: string) {
    const item = await catalogRequest<AttributeRecord>("categories", "POST", { name, description });
    setCatalog(previous => ({ ...previous, categories: [...previous.categories, item] }));
    setProduct(previous => ({ ...previous, category: item.name }));
    return true;
  }
  const [excluded, setExcluded] = useState(() => {
    const present = new Set(initial.variants.map(item => keyOf(item.color, item.size)));
    return new Set(colors.flatMap(color => sizes.map(size => keyOf(color, size))).filter(key => !present.has(key)));
  });
  const [price, setPrice] = useState("");
  const [wholesale, setWholesale] = useState("");
  const [showMedia, setShowMedia] = useState(false);
  const combinations = colors.flatMap(color => sizes.map(size => ({ color, size })));
  const active = combinations.filter(item => !excluded.has(keyOf(item.color, item.size)));
  const variants = active.map(({ color, size }) => product.variants.find(item => item.color === color && item.size === size) ?? product.inactiveVariants?.find(item => item.color === color && item.size === size) ??
    { color, size, sku: "", barcode: "", price, wholesale });
  function applyPrice(field: "price" | "wholesale", value: string, valid: boolean) {
    if (!valid) return;
    if (field === "price") setPrice(value);
    else setWholesale(value);
    const activeKeys = new Set(variants.map(item => keyOf(item.color, item.size)));
    setProduct(previous => ({
      ...previous,
      variants: [...previous.variants.filter(item => !activeKeys.has(keyOf(item.color, item.size))),
        ...variants.map(item => ({ ...item, [field]: value }))],
    }));
  }

  function codes(item: Variant) {
    const identity = initial.id + keyOf(item.color, item.size);
    let hash = BigInt("14695981039346656037");
    for (const char of identity) hash = BigInt.asUintN(64, (hash ^ BigInt(char.charCodeAt(0))) * BigInt("1099511628211"));
    const digits = "2" + (hash % BigInt("100000000000")).toString().padStart(11, "0");
    const sum = [...digits].reduce((total, digit, index) => total + Number(digit) * (index % 2 ? 3 : 1), 0);
    return {
      sku: item.sku || (autoSku ? "PRO-" + hash.toString(36).toUpperCase() : ""),
      barcode: item.barcode || (autoBarcode ? digits + ((10 - sum % 10) % 10) : ""),
    };
  }

  function update(item: Variant) {
    setProduct(previous => ({ ...previous, variants: [...previous.variants.filter(value => keyOf(value.color, value.size) !== keyOf(item.color, item.size)), item] }));
  }
  return <><form className="kp kp-editor-form" onSubmit={async event => {
    event.preventDefault();
    if (submitting) return;
    if (!product.name.trim() || !product.category.trim() || !variants.length) { setError("Completa el nombre, la categoría y al menos una variante."); return; }
    if (!records.some(record => record.id === initial.id) && variants.length > 100) { setError("El backend admite un máximo de 100 variantes por producto."); return; }
    const normalized = variants.map(item => {
      const resolved = codes(item);
      return { ...item, sku: resolved.sku.trim(), barcode: resolved.barcode.trim() };
    });
    const used = records.filter(record => record.id !== initial.id).flatMap(record => record.variants);
    if (normalized.some(item => !item.sku || used.some(other => other.sku.toUpperCase() === item.sku.toUpperCase()))) {
      setError("Revisa los SKU: deben estar completos y ser únicos en el catálogo."); return;
    }
    const barcodes = normalized.map(item => item.barcode).filter(Boolean);
    if (new Set(barcodes).size !== barcodes.length || used.some(item => item.barcode && barcodes.includes(item.barcode))) {
      setError("Los códigos de barras deben ser únicos en el catálogo."); return;
    }
    const skuSet = new Set(normalized.map(item => item.sku.toUpperCase()));
    if (skuSet.size !== normalized.length) { setError("Cada variante debe tener un SKU distinto."); return; }
    setSubmitting(true);
    try { await onSave({ ...product, name: product.name.trim(), category: product.category.trim(), variants: normalized, autoSku, autoBarcode }); } finally { setSubmitting(false); }
  }}>
    <div className="kp-between kp-heading"><h2>{initial.name ? "Editar producto" : "Nuevo producto"}</h2><span className="kp-muted">Cambios pendientes</span></div>
    <div className="kp-editor">
      <section className="kp-fields kp-product-info" aria-label="Datos del producto">
        <div className="kp-card kp-upload-card">
          <div className="kp-photo-frame"><Picture image={product.image} />{!product.image && <strong>{product.name || "Nombre del producto"}</strong>}</div>
          <button className="kp-file" type="button" onClick={() => setShowMedia(true)}>Haz clic para subir imagen</button>
        </div>
        <div className="kp-attribute-triggers">
          <button type="button" onClick={() => setDrawer("colors")}><Palette size={15} /> Colores <span className="kp-badge">{colors.length}</span></button>
          <button type="button" onClick={() => setDrawer("sizes")}><Ruler size={15} /> Tallas <span className="kp-badge">{sizes.length}</span></button>
        </div>
        {(colors.length > 0 || sizes.length > 0) && <div className="kp-summary">
          <div className="kp-chips">{colors.map(name => <span key={name} title={name}><Swatch color={attributes.colors.find(color => color.name === name)} /></span>)}</div>
          <div className="kp-chips">{sizes.map(size => <span className="kp-pill" key={size}>{size}</span>)}</div>
        </div>}
        <CategoryPicker value={product.category} options={categories} onCreate={addCategory}
          onSelect={category => setProduct(previous => ({ ...previous, category }))} />
        <label>Nombre del producto *<input required placeholder="Ej. Polo algodón pima" maxLength={100} value={product.name} onChange={event => setProduct({ ...product, name: event.target.value })} /></label>
        <label>Descripción<textarea placeholder="Pima 100%…" maxLength={500} rows={4} value={product.description} onChange={event => setProduct({ ...product, description: event.target.value })} /></label>
      </section>
      <section className="kp-matrix" aria-label="Matriz de variantes">
        <div className="kp-between kp-heading"><h2><Grid2X2 size={16} /> Matriz de variantes</h2><div className="kp-matrix-controls">
          <span className="kp-badge">{variants.length} {variants.length === 1 ? "combinación" : "combinaciones"}</span>
          <div className="kp-view-switch" role="group" aria-label="Vista de variantes">
            <button type="button" aria-label="Tarjetas" aria-pressed={view === "cards"} onClick={() => setView("cards")}><Grid2X2 size={15} /></button>
            <button type="button" aria-label="Lista" aria-pressed={view === "list"} onClick={() => setView("list")}><List size={15} /></button>
          </div>
          <label className="kp-switch">SKU automático<input type="checkbox" role="switch" disabled={records.some(record => record.id === initial.id)} checked={autoSku} onChange={event => setAutoSku(event.target.checked)} /></label>
          <label className="kp-switch">Código de barras automático<input type="checkbox" role="switch" disabled={records.some(record => record.id === initial.id)} checked={autoBarcode} onChange={event => setAutoBarcode(event.target.checked)} /></label>
        </div></div>
        <div className="kp-card kp-bulk">
          <label>Precio regular para todas<input type="number" min="0" step="0.01" value={price} onChange={event => applyPrice("price", event.target.value, event.target.validity.valid)} /></label>
          <label>Precio mayorista para todas<input type="number" min="0" step="0.01" value={wholesale} onChange={event => applyPrice("wholesale", event.target.value, event.target.validity.valid)} /></label>
        </div>
        <details className="kp-stock-bulk">
          <summary>Aplicar stock por sucursal</summary>
          <div className="kp-chips">
            <button type="button" aria-pressed={!bulkBranch} onClick={() => setBulkBranch("")}>Todas</button>
            {branchItems.map(branch => <button type="button" key={branch.id} aria-pressed={branch.id === bulkBranch} onClick={() => setBulkBranch(branch.id)}>{branch.name}</button>)}
          </div>
          {branches.loading ? <p className="kp-muted">Cargando sucursales…</p> : branches.error ? <p className="kp-muted">{branches.error} {canReadBranches && <button type="button" onClick={branches.retry}>Reintentar</button>}</p> : !branchItems.length ? <p className="kp-muted">No hay sucursales activas.</p> : <div className="kp-chips">
            <label>Cantidad <input type="number" min="0" step="1" value={bulkQuantity} onChange={event => setBulkQuantity(event.target.value)} /></label>
            <button type="button" disabled={!variants.some(item => !item.id) || bulkQuantity === "" || !Number.isSafeInteger(Number(bulkQuantity)) || Number(bulkQuantity) < 0}
              onClick={() => {
                const patch = Object.fromEntries(branchItems.filter(branch => !bulkBranch || branch.id === bulkBranch).map(branch => [branch.id, Number(bulkQuantity)]));
                setProduct(previous => ({ ...previous, variants: variants.map(item => item.id ? item : ({ ...item, stock: { ...item.stock, ...patch } })) }));
              }}>Aplicar a variantes nuevas</button>
          </div>}
        </details>
        {combinations.length > 0 && <div className="kp-card kp-axes kp-between">
          <div className="kp-chips"><span className="kp-muted">Tallas</span>{sizes.map(size => <span className="kp-pill" key={size}>{size}</span>)}</div>
          <div className="kp-chips"><span className="kp-muted">Colores</span>{colors.map(name => <span key={name} title={name}><Swatch color={attributes.colors.find(color => color.name === name)} /></span>)}</div>
        </div>}
        {!combinations.length && <div className="kp-card kp-empty kp-variants-empty"><Image src="/product-variants.png" width={500} height={500} alt="Un producto combinado en variantes de distintos colores" /><p>Selecciona al menos un color y una talla para generar automáticamente la matriz de variantes.</p></div>}
        {active.length < combinations.length && <button type="button" onClick={() => setExcluded(new Set())}>Restaurar combinaciones excluidas</button>}
        <div className={"kp-variants" + (view === "list" ? " kp-variants-list kp-card" : "")}>
          {view === "list" && variants.length > 0 && <div className="kp-list-head"><span>Variante</span><span>SKU y código</span><span>Stock por sucursal</span><span>Precios</span><span>Acciones</span></div>}
          {variants.map(item => {
            const key = keyOf(item.color, item.size);
            const resolved = codes(item);
            return <article className="kp-card kp-variant" key={key}>
              <header><div className="kp-variant-title"><Swatch color={attributes.colors.find(color => color.name === item.color)} /><div><h3>{item.color}</h3><span className="kp-muted">Talla {item.size}</span></div></div></header>
              <div className="kp-fields kp-two kp-codes">
                <label>SKU<input required value={resolved.sku} readOnly={autoSku || !!item.id} maxLength={60} onChange={event => update({ ...item, sku: event.target.value })} /></label>
                <label>Código de barras<input value={resolved.barcode} readOnly={autoBarcode} maxLength={40} onChange={event => update({ ...item, barcode: event.target.value })} /></label>
              </div>
              <div className="kp-variant-stock">
                <p className="kp-muted">{item.id ? "Stock actual · ajustes en Movimientos" : "Stock inicial por sucursal"}</p>
                <div className="kp-stock-tabs" role="group" aria-label="Tipo de sucursal">
                  <button type="button" aria-pressed={stockGroup === "sales"} onClick={() => setStockGroup("sales")}><Store size={13} /> Ventas</button>
                  <button type="button" aria-pressed={stockGroup === "warehouse"} onClick={() => setStockGroup("warehouse")}><Warehouse size={13} /> Almacén</button>
                </div>
                <div className="kp-stock-rows">{visibleBranches.length ? visibleBranches.map(branch => <label key={branch.id}>
                  <span>{branch.name}</span><input type="number" min="0" step="1" placeholder="0" readOnly={!!item.id} value={item.stock?.[branch.id] ?? ""}
                    onChange={event => {
                      const value = event.target.valueAsNumber;
                      if (event.target.value === "" || (Number.isSafeInteger(value) && value >= 0)) update({ ...item, stock: { ...item.stock, [branch.id]: Number.isNaN(value) ? 0 : value } });
                    }} />
                </label>) : <p className="kp-muted">{branches.loading ? "Cargando sucursales…" : branches.error || "Sin sucursales de este tipo."}</p>}</div>
              </div>
              <div className="kp-fields kp-two kp-prices">
                <label>Precio regular (S/)<input required type="number" min="0" step="0.01" value={item.price} onChange={event => update({ ...item, price: event.target.value })} /></label>
                <label>Precio mayorista (S/)<input type="number" min="0" step="0.01" value={item.wholesale} onChange={event => update({ ...item, wholesale: event.target.value })} /></label>
              </div>
              <button className="kp-remove-variant" type="button" aria-label={"Excluir " + item.color + " " + item.size} onClick={() => setExcluded(previous => new Set(previous).add(key))}><X size={16} /></button>
            </article>;
          })}
        </div>
      </section>
    </div>
    <footer className="kp-actions"><span role="alert">{error || saveError}</span><button type="button" disabled={submitting} onClick={onCancel}>Descartar</button><button className="kp-primary" disabled={!variants.length || submitting}>{submitting ? "Guardando…" : "Guardar producto"}</button></footer>
  </form>
    {showMedia && createPortal(<MediaDrawer image={product.image} galleries={product.colorImages ?? {}}
      colors={colors.map(name => attributes.colors.find(color => color.name === name) ?? { name })}
      onClose={() => setShowMedia(false)} onSave={(image, colorImages) => {
        setProduct(previous => ({ ...previous, image, colorImages }));
        setShowMedia(false);
      }} />, document.body)}
    {drawer && createPortal(<AttributeDrawer initialKind={drawer} options={attributes} selected={{ colors, sizes }}
      onToggle={toggle} onCreate={async (kind, value) => {
        const item = await catalogRequest<AttributeRecord>(kind, "POST", value);
        setCatalog(previous => ({ ...previous, [kind]: [...previous[kind], item] }));
        toggle(kind, value.name);
        return true;
      }} onClose={() => setDrawer(undefined)} />, document.body)}
  </>;
}
