"use client";
import { useEffect } from "react";

import { notify } from "@/lib/notifications";
export function LoginSessionNotice() {
  useEffect(() => {
    const url = new URL(window.location.href);
    if (url.searchParams.get("session") !== "expired") return;
    notify.info("Tu sesión finalizó. Inicia sesión nuevamente.", { id: "auth:session" });
    url.searchParams.delete("session");
    window.history.replaceState(null, "", `${url.pathname}${url.search}${url.hash}`);
  }, []);
  return null;
}
