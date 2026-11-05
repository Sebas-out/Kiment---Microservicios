import {
  LayoutDashboard, ShoppingCart, FileText, UsersRound, History, FileClock, Files,
  Package, Layers3, Tag, Palette, Boxes, ArrowLeftRight, ChartNoAxesCombined,
  Users, Building2, Settings2, CreditCard, CircleUserRound, type LucideIcon,
} from "lucide-react";
import { canReadUsers, type AuthProfile, type Role } from "../auth/contracts";

type Permission = "SALE_CREATE" | "QUOTATION_CREATE" | "CUSTOMER_READ" | "INVOICE_READ" |
  "PRODUCT_READ" | "CATEGORY_WRITE" | "VARIANT_WRITE" | "STOCK_READ" | "MOVEMENT_READ" | "REPORT_READ" | "STOCK_REPORT_READ" | "USER_READ" | "BRANCH_READ";
export type NavigationItem = {
  id: string; label: string; icon: LucideIcon; href?: "/dashboard" | "/usuarios" | "/sucursales" | "/mi-cuenta";
  permission?: Permission; anyPermission?: readonly Permission[]; roles?: readonly Role[];
};
export type NavigationGroup = { id: string; label: string; items: readonly NavigationItem[] };

export const navigation: readonly NavigationGroup[] = [
  { id: "principal", label: "Principal", items: [
    { id: "dashboard", label: "Dashboard", icon: LayoutDashboard, href: "/dashboard" },
  ] },
  { id: "operaciones", label: "Operaciones", items: [
    { id: "ventas", label: "Ventas POS", icon: ShoppingCart, permission: "SALE_CREATE" },
    { id: "cotizaciones", label: "Cotizaciones", icon: FileText, permission: "QUOTATION_CREATE" },
    { id: "clientes", label: "Clientes", icon: UsersRound, permission: "CUSTOMER_READ" },
    // No read-history authority is defined by this backend yet. Remain disabled.
    { id: "historial-ventas", label: "Historial de ventas", icon: History },
    { id: "historial-cotizaciones", label: "Historial de cotizaciones", icon: FileClock },
  ] },
  { id: "facturacion", label: "Facturación", items: [
    { id: "comprobantes", label: "Comprobantes", icon: Files, permission: "INVOICE_READ" },
  ] },
  { id: "catalogo", label: "Catálogo", items: [
    { id: "productos", label: "Productos", icon: Package, permission: "PRODUCT_READ" },
    { id: "categorias", label: "Categorías", icon: Layers3, permission: "CATEGORY_WRITE" },
    { id: "tallas", label: "Tallas", icon: Tag, permission: "VARIANT_WRITE" },
    { id: "colores", label: "Colores", icon: Palette, permission: "VARIANT_WRITE" },
  ] },
  { id: "inventario", label: "Inventario", items: [
    { id: "stock", label: "Stock", icon: Boxes, permission: "STOCK_READ" },
    { id: "movimientos", label: "Movimientos", icon: ArrowLeftRight, permission: "MOVEMENT_READ" },
  ] },
  { id: "reportes", label: "Reportes", items: [
    { id: "reportes", label: "Reportes", icon: ChartNoAxesCombined, anyPermission: ["REPORT_READ", "STOCK_REPORT_READ"] },
  ] },
  { id: "administracion", label: "Administración", items: [
    { id: "usuarios", label: "Usuarios", icon: Users, href: "/usuarios", permission: "USER_READ", roles: ["ADMIN"] },
    { id: "sucursales", label: "Sucursales", icon: Building2, href: "/sucursales", permission: "BRANCH_READ", roles: ["ADMIN"] },
  ] },
  { id: "configuracion", label: "Configuración", items: [
    { id: "empresa", label: "Empresa", icon: Settings2, roles: ["ADMIN"] },
    { id: "metodos-pago", label: "Métodos de pago", icon: CreditCard, roles: ["ADMIN"] },
    { id: "mi-cuenta", label: "Mi cuenta", icon: CircleUserRound, href: "/mi-cuenta" },
  ] },
];

export function navigationFor(profile: AuthProfile): NavigationGroup[] {
  if (!profile.user.enabled) return [];
  return navigation.map(group => ({ ...group, items: group.items.filter(item => {
    if (item.href === "/usuarios") return canReadUsers(profile);
    return (!item.roles || item.roles.includes(profile.user.role)) &&
      (!item.permission || profile.permissions.includes(item.permission)) &&
      (!item.anyPermission || item.anyPermission.some(permission => profile.permissions.includes(permission)));
  }) })).filter(group => group.items.length > 0);
}

// Pass only identifiers to the client. Lucide component functions stay in its module.
export function allowedNavigationIds(profile: AuthProfile): string[] {
  return navigationFor(profile).flatMap(group => group.items.map(item => item.id));
}
