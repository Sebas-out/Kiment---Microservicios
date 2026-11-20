"use client";
import { restoreSession, sessionGeneration } from "@/features/auth/session-client";
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type AttributeRecord = { id: string; name: string; hex?: string; description?: string; enabled: boolean };
export type RemoteVariant = { id: string; productId: string; colorId: string; colorName: string; sizeId: string; sizeName: string; sku: string; barcode: string; price: number; wholesalePrice: number | null; stockByBranch: Record<string, number>; colorHex?: string; enabled: boolean };
export type RemoteImage = { id: string; colorId: string | null; url: string };
export type RemoteProduct = { id: string; name: string; categoryId: string; categoryName: string; description: string; autoSku: boolean; autoBarcode: boolean; enabled: boolean; variants: RemoteVariant[]; images: RemoteImage[] };
export async function catalogRequest<T>(path: string, method = "GET", body?: unknown, key?: string): Promise<T> {
  const generation = sessionGeneration();
  const send = () => fetch("/api/catalog/" + path, { method, credentials: "same-origin", cache: "no-store", signal: AbortSignal.timeout(35000), headers: { ...(body instanceof FormData ? {} : { "Content-Type": "application/json" }), ...(key ? { "Idempotency-Key": key } : {}) }, body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body) });
  let response = await send();
  if (response.status === 401) {
    await restoreSession(generation);
    if (method !== "GET") throw new Error("Sesión renovada. Revisa los datos y vuelve a guardar.");
    response = await send();
  }
  if (sessionGeneration() !== generation) throw new Error("La sesión cambió. Recarga la página.");
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) ?? {};
    const fields = Array.isArray(problem.fields) ? problem.fields.map((field: { message?: string }) => field.message).filter(Boolean).join(". ") : "";
    throw new Error(fields || problem.error || "No se pudo completar la operación.");
  }
  if (response.status === 204) return undefined as T;
  if (response.headers.get("Content-Type")?.includes("spreadsheetml")) return await response.blob() as T;
  return response.json();
}
export async function allRecords<T>(path: string): Promise<T[]> {
  const result: T[] = [];
  for (let page = 0; ; page++) {
    const response = await catalogRequest<Page<T>>(path + (path.includes("?") ? "&" : "?") + "page=" + page + "&size=100");
    if (!Array.isArray(response?.content) || !Number.isInteger(response.totalPages)) throw new Error("Respuesta de paginación inválida.");
    result.push(...response.content);
    if (page + 1 >= response.totalPages) return result;
    if (page >= 999) throw new Error("Demasiados resultados. Reduce la búsqueda.");
  }
}
export function imageUrl(productId: string, imageId: string) { return "/api/catalog/products/" + productId + "/images/" + imageId; }
export function mapProduct(product: RemoteProduct) {
  const variants = product.variants.map(row => ({ ...row, color: row.colorName, size: row.sizeName, price: String(row.price), wholesale: row.wholesalePrice == null ? "" : String(row.wholesalePrice), stock: row.stockByBranch }));
  const colors = Object.fromEntries(product.variants.map(row => [row.colorId, row.colorName]));
  return { ...product, category: product.categoryName, image: product.images.filter(image => !image.colorId).map(image => imageUrl(product.id, image.id))[0] ?? "",
    colorImages: Object.fromEntries(Object.entries(colors).map(([id, name]) => [name, product.images.filter(image => image.colorId === id).map(image => imageUrl(product.id, image.id))])),
    variants: variants.filter(row => row.enabled), inactiveVariants: variants.filter(row => !row.enabled) };
}
