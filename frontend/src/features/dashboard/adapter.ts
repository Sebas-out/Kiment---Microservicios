import type { DashboardFilters, DashboardResult } from "./types";
// No URL is invented. Replace this boundary once authorized commercial APIs exist.
export async function loadDashboard(filters: DashboardFilters, signal?: AbortSignal): Promise<DashboardResult> {
  void filters;
  signal?.throwIfAborted();
  return { status: "unavailable", data: { totals: { sales: null, count: null, average: null, products: null }, sales: [], payments: [], documents: [], products: [], stock: [] } };
}
