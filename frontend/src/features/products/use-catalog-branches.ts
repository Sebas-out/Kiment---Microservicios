"use client";
import { useBranchOptions } from "@/features/branches/use-branch-options";
import type { Branch } from "@/features/branches/contracts";

type BranchOption = Pick<Branch, "id" | "name" | "city" | "branchType">;
// Auth exposes the branch directory only to administrators. Operative users
// retain their assigned scope without requesting the protected directory.
export function useCatalogBranches(canReadAll: boolean, assignedBranchId?: string | null) {
  const directory = useBranchOptions(canReadAll);
  const assigned: BranchOption[] = assignedBranchId ? [{ id: assignedBranchId, name: "Sucursal asignada", city: null, branchType: null }] : [];
  return { ...directory, items: canReadAll ? directory.items : assigned, loading: canReadAll && directory.loading, error: canReadAll ? directory.error : undefined };
}
