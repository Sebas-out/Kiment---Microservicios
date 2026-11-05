"use client";

import { toast, type ExternalToast } from "sonner";

type NotificationOptions = Pick<ExternalToast, "id" | "description" | "duration">;
type NotificationType = "success" | "error" | "warning" | "info" | "loading";

function show(type: NotificationType, message: string, options: NotificationOptions = {}) {
  return toast[type](message, {
    duration: 4000,
    ...options,
    // A repeated event updates its toast instead of stacking copies.
    id: options.id ?? `kiments:${type}:${message}`,
  });
}

/** Use a shared id to replace a loading notification with its final result. */
export const notify = {
  success: (message: string, options?: NotificationOptions) => show("success", message, options),
  error: (message: string, options?: NotificationOptions) => show("error", message, options),
  warning: (message: string, options?: NotificationOptions) => show("warning", message, options),
  info: (message: string, options?: NotificationOptions) => show("info", message, options),
  // Sonner keeps loading visible until it is updated or dismissed by the caller.
  loading: (message: string, options?: NotificationOptions) => show("loading", message, options),
  dismiss: (id?: string | number) => toast.dismiss(id),
};
