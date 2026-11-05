"use client";
import { useEffect, useState } from "react";
import { usersRequest } from "@/features/users/client";
import { collectBranches, type Branch, type BranchPage } from "./contracts";

export function useBranchOptions(allowed: boolean, activeOnly = true) {
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ key: string; items?: Branch[]; error?: string }>();
  const key = `${allowed}:${activeOnly}:${revision}`;
  useEffect(() => {
    if (!allowed) return;
    const controller = new AbortController();
    collectBranches(page => usersRequest<BranchPage>(`branches?size=100&page=${page}${activeOnly ? "&enabled=true" : ""}`, "GET", undefined, controller.signal))
      .then(items => { if (!controller.signal.aborted) setState({ key, items }); })
      .catch(error => { if (!controller.signal.aborted) setState({ key, error: error.message }); });
    return () => controller.abort();
  }, [allowed, activeOnly, key]);
  const current = state?.key === key ? state : undefined;
  return { items: current?.items, error: !allowed ? "No tienes permiso para consultar sucursales." : current?.error, loading: allowed && !current, retry: () => setRevision(v => v + 1) };
}
