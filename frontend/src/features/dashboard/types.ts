// Presentation models, not a claimed backend DTO. Future API adapters map into these.
export type DatePreset = "today" | "yesterday" | "week" | "month" | "custom";
export type DashboardFilters = { preset: DatePreset; from: string; to: string; branchId: string | null };
export type Breakdown = { label: string; value: number };
export type ProductSales = { id: string; name: string; variant?: string; quantity: number; amount: number };
export type CriticalStock = { id: string; name: string; variant?: string; available: number; level: "out" | "low" };
export type DashboardData = {
  totals: { sales: number | null; count: number | null; average: number | null; products: number | null };
  sales: { date: string; amount: number }[];
  payments: Breakdown[]; documents: Breakdown[]; products: ProductSales[]; stock: CriticalStock[];
};
export type DashboardResult = { status: "unavailable" | "ready"; data: DashboardData };
