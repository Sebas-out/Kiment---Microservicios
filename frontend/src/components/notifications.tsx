"use client";

import { useSyncExternalStore } from "react";
import { Toaster } from "sonner";
import { CircleCheck, CircleAlert, TriangleAlert, Info, LoaderCircle, X } from "lucide-react";

function subscribeToTheme(onChange: () => void) {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
  return () => observer.disconnect();
}

function getTheme() {
  return document.documentElement.dataset.theme === "dark" ? "dark" : "light";
}

export function Notifications() {
  const theme = useSyncExternalStore<"light" | "dark">(subscribeToTheme, getTheme, () => "light");

  return <Toaster
    className="kiments-toaster"
    position="top-center"
    theme={theme}
    duration={4000}
    closeButton
    visibleToasts={3}
    gap={10}
    offset={24}
    mobileOffset={{ top: 16, left: 16, right: 16 }}
    containerAriaLabel="Notificaciones"
    icons={{
      success: <CircleCheck size={19} strokeWidth={1.7} aria-hidden="true" />,
      error: <CircleAlert size={19} strokeWidth={1.7} aria-hidden="true" />,
      warning: <TriangleAlert size={19} strokeWidth={1.7} aria-hidden="true" />,
      info: <Info size={19} strokeWidth={1.7} aria-hidden="true" />,
      loading: <LoaderCircle size={19} strokeWidth={1.7} className="animate-spin" aria-hidden="true" />,
      close: <X size={14} strokeWidth={1.8} aria-hidden="true" />,
    }}
    toastOptions={{
      classNames: { toast: "kiments-toast", title: "kiments-toast-title", description: "kiments-toast-description", closeButton: "kiments-toast-close" },
      closeButtonAriaLabel: "Cerrar notificación",
    }}
  />;
}

