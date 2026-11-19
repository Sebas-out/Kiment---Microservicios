import type { Attribute } from "./attribute-drawer";

export type Catalog = {
  colors: Attribute[];
  sizes: Attribute[];
  categories: string[];
  categoryDescriptions?: Record<string, string>;
  hidden?: Partial<Record<"colors" | "sizes" | "categories", string[]>>;
};
export type Kind = "colors" | "sizes" | "categories";
export const defaultCategories = ["Pantalones", "Prendas únicas", "Conjunto"];
export const commonSizes = ["XS", "S", "M", "L", "XL"];
export const catalogKeyFor = (account: string) => "kiments:product-drafts:" + account + ":attributes";
export const normalizeCatalog = (value: string) => value.trim().normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLocaleLowerCase("es");
export function readCatalog(key: string): Catalog {
  try { return JSON.parse(localStorage.getItem(key) ?? "null") ?? { colors: [], sizes: [], categories: [] }; }
  catch { return { colors: [], sizes: [], categories: [] }; }
}
export function visibleCatalog(items: Attribute[], catalog: Catalog, kind: Kind) {
  const hidden = new Set(catalog.hidden?.[kind]);
  return [...new Map(items.map(item => [item.name, item])).values()].filter(item => !hidden.has(item.name));
}