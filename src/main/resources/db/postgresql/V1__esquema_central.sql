-- Fechas ISO en texto conservan los convertidores de la aplicacion y los datos anteriores.
CREATE TABLE productos (
 id BIGSERIAL PRIMARY KEY, codigo_barras TEXT UNIQUE, sku TEXT NOT NULL,
 descripcion TEXT NOT NULL, stock_actual INTEGER NOT NULL DEFAULT 0,
 stock_minimo INTEGER NOT NULL DEFAULT 5, precio_venta_pen NUMERIC NOT NULL,
 costo_promedio_pen NUMERIC DEFAULT 0,
 CONSTRAINT stock_no_negativo CHECK (stock_actual >= 0 AND stock_minimo >= 0)
);
CREATE TABLE usuarios (
 id BIGSERIAL PRIMARY KEY, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL,
 nombre_completo TEXT NOT NULL, rol TEXT NOT NULL, activo BOOLEAN NOT NULL DEFAULT TRUE,
 creado_en TEXT DEFAULT to_char(localtimestamp, 'YYYY-MM-DD HH24:MI:SS.MS')
);
CREATE TABLE lotes (
 id BIGSERIAL PRIMARY KEY, producto_id BIGINT NOT NULL REFERENCES productos(id),
 codigo_lote TEXT NOT NULL, numero_dua TEXT, cantidad_inicial INTEGER NOT NULL,
 cantidad_disponible INTEGER NOT NULL CHECK (cantidad_disponible >= 0),
 costo_unitario_usd NUMERIC, costo_unitario_pen NUMERIC NOT NULL,
 fecha_ingreso TEXT NOT NULL, fecha_vencimiento TEXT, ubicacion TEXT
);
CREATE INDEX lotes_fifo ON lotes(producto_id, fecha_ingreso, id);
CREATE TABLE cajas (numero INTEGER PRIMARY KEY, nombre TEXT NOT NULL);
INSERT INTO cajas VALUES (1, 'Caja 1'), (2, 'Caja 2'), (3, 'Caja 3');
CREATE TABLE caja_turno (
 id BIGSERIAL PRIMARY KEY, numero_caja INTEGER NOT NULL REFERENCES cajas(numero),
 fecha_apertura TEXT NOT NULL, fecha_cierre TEXT, monto_apertura NUMERIC NOT NULL,
 monto_efectivo_ventas NUMERIC DEFAULT 0, monto_yape_plin NUMERIC DEFAULT 0,
 monto_tarjeta NUMERIC DEFAULT 0, monto_mixto NUMERIC DEFAULT 0,
 monto_contado NUMERIC, monto_esperado NUMERIC, diferencia NUMERIC,
 usuario_id BIGINT REFERENCES usuarios(id), estado TEXT NOT NULL DEFAULT 'ABIERTA'
);
CREATE UNIQUE INDEX una_apertura_por_caja ON caja_turno(numero_caja) WHERE estado = 'ABIERTA';
CREATE TABLE ventas (
 id BIGSERIAL PRIMARY KEY, fecha_emision TEXT NOT NULL, tipo_comprobante TEXT NOT NULL,
 serie TEXT NOT NULL, correlativo INTEGER NOT NULL, cliente_documento TEXT, cliente_nombre TEXT,
 subtotal NUMERIC NOT NULL, igv NUMERIC NOT NULL, total NUMERIC NOT NULL,
 metodo_pago TEXT NOT NULL, caja_turno_id BIGINT REFERENCES caja_turno(id),
 usuario_id BIGINT REFERENCES usuarios(id), estado TEXT NOT NULL DEFAULT 'COMPLETADA',
 UNIQUE(serie, correlativo)
);
CREATE INDEX ventas_fecha ON ventas(fecha_emision);
CREATE TABLE detalle_venta (
 id BIGSERIAL PRIMARY KEY, venta_id BIGINT NOT NULL REFERENCES ventas(id) ON DELETE CASCADE,
 producto_id BIGINT NOT NULL REFERENCES productos(id), cantidad INTEGER NOT NULL CHECK (cantidad > 0),
 precio_unitario NUMERIC NOT NULL, lote_id BIGINT REFERENCES lotes(id), subtotal NUMERIC NOT NULL
);
CREATE TABLE comprobantes_sunat (
 id BIGSERIAL PRIMARY KEY, tipo_comprobante TEXT NOT NULL, serie TEXT NOT NULL,
 correlativo INTEGER NOT NULL, cliente_documento TEXT, cliente_nombre TEXT, total NUMERIC NOT NULL,
 estado_sunat TEXT NOT NULL, hash_cpe TEXT, fecha_emision TEXT NOT NULL,
 venta_id BIGINT UNIQUE REFERENCES ventas(id), subtotal NUMERIC, igv NUMERIC, metodo_pago TEXT,
 emisor_nombre TEXT, emisor_ruc TEXT, emisor_direccion TEXT, pdf_documento BYTEA,
 UNIQUE(serie, correlativo)
);
CREATE TABLE comprobante_detalle (
 id BIGSERIAL PRIMARY KEY, comprobante_id BIGINT NOT NULL REFERENCES comprobantes_sunat(id) ON DELETE CASCADE,
 descripcion TEXT NOT NULL, cantidad INTEGER NOT NULL, precio_unitario NUMERIC NOT NULL, importe NUMERIC NOT NULL
);
CREATE TABLE datos_empresa (
 id INTEGER PRIMARY KEY, razon_social TEXT NOT NULL, ruc TEXT NOT NULL DEFAULT '', direccion TEXT NOT NULL DEFAULT ''
);
INSERT INTO datos_empresa VALUES (1, 'Importadora Perú', '', '');
CREATE TABLE secuencias_comprobante (serie TEXT PRIMARY KEY, siguiente INTEGER NOT NULL);
INSERT INTO secuencias_comprobante VALUES ('B001', 1), ('F001', 1);
CREATE TABLE tipo_cambio (
 id BIGSERIAL PRIMARY KEY, fecha TEXT NOT NULL UNIQUE, compra NUMERIC NOT NULL,
 venta NUMERIC NOT NULL, origen TEXT DEFAULT 'SUNAT'
);
CREATE TABLE historial_precio (
 id BIGSERIAL PRIMARY KEY, producto_id BIGINT NOT NULL REFERENCES productos(id),
 tipo_cambio_anterior NUMERIC, precio_anterior NUMERIC, tipo_cambio_nuevo NUMERIC,
 precio_nuevo NUMERIC, diferencia NUMERIC, fecha_cambio TEXT NOT NULL, usuario_id BIGINT REFERENCES usuarios(id)
);
CREATE TABLE reposicion_config (
 producto_id BIGINT PRIMARY KEY REFERENCES productos(id), ventas_dia NUMERIC,
 dias_entrega INTEGER DEFAULT 30, stock_seguridad INTEGER DEFAULT 10, proveedor TEXT
);
CREATE TABLE pedidos_reposicion (
 id BIGSERIAL PRIMARY KEY, producto_id BIGINT NOT NULL REFERENCES productos(id),
 cantidad INTEGER NOT NULL, proveedor TEXT, costo_usd NUMERIC, costo_pen NUMERIC,
 estado TEXT NOT NULL DEFAULT 'PENDIENTE', creado_en TEXT NOT NULL DEFAULT to_char(localtimestamp, 'YYYY-MM-DD HH24:MI:SS.MS')
);
CREATE TABLE proveedores (
 id BIGSERIAL PRIMARY KEY, ruc TEXT UNIQUE, razon_social TEXT NOT NULL, contacto TEXT,
 telefono TEXT, correo TEXT, direccion TEXT, pais TEXT DEFAULT 'Perú', estado TEXT NOT NULL DEFAULT 'ACTIVO',
 creado_en TEXT NOT NULL DEFAULT to_char(localtimestamp, 'YYYY-MM-DD HH24:MI:SS.MS')
);
CREATE TABLE compras_dua (
 id BIGSERIAL PRIMARY KEY, proveedor TEXT NOT NULL, ruc TEXT, numero_dua TEXT NOT NULL,
 fecha_ingreso TEXT NOT NULL, tipo_cambio NUMERIC NOT NULL, total_fob_usd NUMERIC NOT NULL,
 total_gastos_usd NUMERIC NOT NULL, total_landed_usd NUMERIC NOT NULL, total_landed_pen NUMERIC NOT NULL,
 estado TEXT NOT NULL DEFAULT 'REGISTRADA', creado_en TEXT NOT NULL DEFAULT to_char(localtimestamp, 'YYYY-MM-DD HH24:MI:SS.MS'),
 proveedor_id BIGINT REFERENCES proveedores(id)
);
CREATE TABLE detalle_compra_dua (
 id BIGSERIAL PRIMARY KEY, compra_id BIGINT NOT NULL REFERENCES compras_dua(id) ON DELETE CASCADE,
 producto_id BIGINT NOT NULL REFERENCES productos(id), lote_id BIGINT NOT NULL REFERENCES lotes(id),
 sku TEXT NOT NULL, descripcion TEXT NOT NULL, codigo_barras TEXT, codigo_lote TEXT NOT NULL,
 ubicacion TEXT, cantidad INTEGER NOT NULL, costo_fob_usd NUMERIC NOT NULL, gastos_dua_usd NUMERIC NOT NULL,
 costo_landed_usd NUMERIC NOT NULL, costo_landed_pen NUMERIC NOT NULL, margen NUMERIC NOT NULL, precio_sugerido_pen NUMERIC NOT NULL
);
CREATE TABLE ubicaciones_almacen (
 id BIGSERIAL PRIMARY KEY, codigo TEXT NOT NULL UNIQUE, pasillo TEXT NOT NULL,
 anaquel INTEGER NOT NULL, nivel INTEGER NOT NULL, activo BOOLEAN NOT NULL DEFAULT TRUE,
 creado_en TEXT DEFAULT to_char(localtimestamp, 'YYYY-MM-DD HH24:MI:SS.MS')
);
