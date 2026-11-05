import type { Metadata } from "next";
import "./globals.css";
import { Notifications } from "@/components/notifications";
export const metadata: Metadata = { title: "KIMENTS | Iniciar sesión", description: "Acceso al punto de venta KIMENTS." };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="es"><body>{children}<Notifications /></body></html>;
}
