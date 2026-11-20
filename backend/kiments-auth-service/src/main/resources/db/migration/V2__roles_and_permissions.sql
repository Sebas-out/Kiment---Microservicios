INSERT INTO auth.roles(code,name,description) VALUES
('ADMIN','Administrador','Administración y operación'),
('VENTAS','Ventas','Operaciones comerciales'),
('ALMACEN','Almacén','Gestión de productos e inventario'),
('VENTAS_ALMACEN','Ventas y almacén','Operaciones comerciales e inventario');
INSERT INTO auth.permissions(code, description) VALUES
('USER_READ','Consultar usuarios'), ('USER_CREATE','Crear usuarios'),
('USER_UPDATE','Editar usuarios'), ('USER_DISABLE','Activar o desactivar usuarios'),
('USER_ROLE_CHANGE','Cambiar roles'),
('PRODUCT_READ','Consultar productos'), ('PRODUCT_WRITE','Editar productos'),
('CATEGORY_WRITE','Gestionar categorías'), ('VARIANT_WRITE','Gestionar tallas y colores'),
('STOCK_READ','Consultar existencias'), ('STOCK_ADJUST','Ajustar existencias'),
('MOVEMENT_READ','Consultar movimientos'), ('SALE_CREATE','Registrar ventas'),
('QUOTATION_CREATE','Crear cotizaciones'), ('CUSTOMER_READ','Consultar clientes'),
('CUSTOMER_WRITE','Editar clientes'), ('PAYMENT_CREATE','Registrar pagos'),
('INVOICE_READ','Consultar comprobantes'), ('INVOICE_CREATE','Emitir comprobantes'),
('REPORT_READ','Consultar reportes'), ('STOCK_REPORT_READ','Consultar reportes de stock');
INSERT INTO auth.role_permissions
SELECT r.id,p.id FROM auth.roles r CROSS JOIN auth.permissions p WHERE
r.code='ADMIN' OR
(r.code IN ('VENTAS','VENTAS_ALMACEN') AND p.code IN
 ('PRODUCT_READ','STOCK_READ','SALE_CREATE','QUOTATION_CREATE','CUSTOMER_READ','CUSTOMER_WRITE','PAYMENT_CREATE','INVOICE_READ','INVOICE_CREATE')) OR
(r.code IN ('ALMACEN','VENTAS_ALMACEN') AND p.code IN
 ('PRODUCT_READ','PRODUCT_WRITE','CATEGORY_WRITE','VARIANT_WRITE','STOCK_READ','STOCK_ADJUST','MOVEMENT_READ','STOCK_REPORT_READ'));
