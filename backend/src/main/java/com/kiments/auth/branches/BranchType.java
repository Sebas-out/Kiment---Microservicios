package com.kiments.auth.branches;

/**
 * Tipo operativo de la sucursal o establecimiento.
 *
 * <ul>
 *   <li>{@code VENTA}: Local comercial / tienda retail que atiende al público y emite Boletas/Facturas.</li>
 *   <li>{@code ALMACEN}: Depósito o centro de distribución. Emite Guías de Remisión y traslados internos.</li>
 *   <li>{@code MIXTO}: Establecimiento comercial que opera simultáneamente como punto de venta y almacén/despacho.</li>
 * </ul>
 */
public enum BranchType {
  VENTA,
  ALMACEN,
  MIXTO
}
