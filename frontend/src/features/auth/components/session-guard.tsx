"use client";

import { useEffect } from "react";
import { readSessionIdentity, restoreSession, sessionGeneration, sessionSignal, SessionError } from "../session-client";

export function SessionGuard({ userId }: { userId: string }) {
  useEffect(() => {
    let active = true;
    let checking = false;
    const initial = sessionGeneration();
    function reset(path: string) {
      if (!active) return;
      active = false;
      document.querySelector<HTMLElement>(".admin-shell")?.style.setProperty("visibility", "hidden");
      window.location.replace(path);
    }
    async function verify() {
      if (!active || checking || document.visibilityState === "hidden") return;
      if (sessionGeneration() !== initial) { reset("/dashboard"); return; }
      checking = true;
      try {
        let identity: string | null;
        try { identity = await readSessionIdentity(); }
        catch (error) {
          if (!(error instanceof SessionError) || error.status !== 401) throw error;
          await restoreSession(initial);
          identity = await readSessionIdentity();
        }
        if (sessionGeneration() !== initial || identity !== userId) reset("/dashboard");
      } catch (error) {
        if (sessionGeneration() !== initial && error instanceof SessionError && error.status !== 409) {
          // A terminal refresh publishes its own change. A different tab's login is
          // resolved server-side rather than clearing its cookies from this handler.
          reset(error.status === 401 ? "/?session=expired" : "/dashboard");
        } else if (error instanceof SessionError) {
          if (error.status === 401) reset("/?session=expired");
          if ([403, 409].includes(error.status)) reset("/dashboard");
        }
      } finally { checking = false; }
    }
    function storage(event: StorageEvent) {
      if ((event.key === sessionSignal || event.key === null) && sessionGeneration() !== initial) reset("/dashboard");
    }
    function pageShow(event: PageTransitionEvent) { if (event.persisted) void verify(); }
    function ended() { reset("/?session=expired"); }
    window.addEventListener("kiments:session-ended", ended);
    window.addEventListener("storage", storage);
    window.addEventListener("focus", verify);
    document.addEventListener("visibilitychange", verify);
    const timer = window.setInterval(verify, 60000);
    window.addEventListener("pageshow", pageShow);
    return () => {
      active = false;
      window.removeEventListener("kiments:session-ended", ended);
      window.removeEventListener("storage", storage);
      window.removeEventListener("focus", verify);
      document.removeEventListener("visibilitychange", verify);
      window.clearInterval(timer);
      window.removeEventListener("pageshow", pageShow);
    };
  }, [userId]);
  return null;
}
