"use client";
import { useEffect, useState } from "react";
import { Moon, Sun } from "lucide-react";
export function ThemeToggle() {
  const [dark, setDark] = useState(false);
  useEffect(() => {
    let saved: string | null = null;
    try { saved = localStorage.getItem("kiments-theme"); } catch {}
    const enabled = saved === "dark" || (!saved && matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.dataset.theme = enabled ? "dark" : "light";
    // Synchronize after hydration; server and first client render are identical.
    queueMicrotask(() => setDark(enabled));
  }, []);
  function toggle() {
    const enabled = !dark;
    setDark(enabled);
    document.documentElement.dataset.theme = enabled ? "dark" : "light";
    try { localStorage.setItem("kiments-theme", enabled ? "dark" : "light"); } catch {}
  }
  return <button className="theme-toggle" onClick={toggle} type="button"
    aria-label={dark ? "Activar tema claro" : "Activar tema oscuro"} aria-pressed={dark}>
    {dark ? <Moon size={16} strokeWidth={1.4} /> : <Sun size={16} strokeWidth={1.4} />}
  </button>;
}
