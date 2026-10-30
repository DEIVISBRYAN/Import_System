-- Esquema local nuevo. No modifica la instalacion PostgreSQL ni bases SQLite anteriores.
CREATE TABLE productos (
 id INTEGER PRIMARY KEY AUTOINCREMENT, codigo_barras TEXT UNIQUE, sku TEXT NOT NULL,
 descripcion TEXT NOT NULL, stock_actual INTEGER NOT NULL DEFAULT 0 CHECK(stock_actual>=0),
 stock_minimo INTEGER NOT NULL DEFAULT 5 CHECK(stock_minimo>=0), precio_venta_pen NUMERIC NOT NULL,
 costo_promedio_pen NUMERIC DEFAULT 0
);
CREATE UNIQUE INDEX producto_sku_unico ON productos(lower(trim(sku)));
CREATE TABLE usuarios (
 id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL,
 nombre_completo TEXT NOT NULL, rol TEXT NOT NULL, activo INTEGER NOT NULL DEFAULT 1,
 creado_en TEXT DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime'))
);
CREATE TABLE lotes (
 id INTEGER PRIMARY KEY AUTOINCREMENT, producto_id INTEGER NOT NULL REFERENCES productos(id),
 codigo_lote TEXT NOT NULL, numero_dua TEXT, cantidad_inicial INTEGER NOT NULL,
 cantidad_disponible INTEGER NOT NULL CHECK(cantidad_disponible>=0), costo_unitario_usd NUMERIC,
 costo_unitario_pen NUMERIC NOT NULL, fecha_ingreso TEXT NOT NULL, fecha_vencimiento TEXT, ubicacion TEXT
);
CREATE INDEX lotes_fifo ON lotes(producto_id,fecha_ingreso,id);
CREATE TABLE cajas(numero INTEGER PRIMARY KEY,nombre TEXT NOT NULL);
INSERT INTO cajas VALUES (1,'Caja 1'),(2,'Caja 2'),(3,'Caja 3');
CREATE TABLE caja_turno (
 id INTEGER PRIMARY KEY AUTOINCREMENT,numero_caja INTEGER NOT NULL REFERENCES cajas(numero),
 fecha_apertura TEXT NOT NULL,fecha_cierre TEXT,monto_apertura NUMERIC NOT NULL,
 monto_efectivo_ventas NUMERIC DEFAULT 0,monto_yape_plin NUMERIC DEFAULT 0,
 monto_tarjeta NUMERIC DEFAULT 0,monto_mixto NUMERIC DEFAULT 0,
 monto_contado NUMERIC,monto_esperado NUMERIC,diferencia NUMERIC,
 usuario_id INTEGER REFERENCES usuarios(id),estado TEXT NOT NULL DEFAULT 'ABIERTA'
);
CREATE UNIQUE INDEX una_apertura_por_caja ON caja_turno(numero_caja) WHERE estado='ABIERTA';
CREATE TABLE ventas (
 id INTEGER PRIMARY KEY AUTOINCREMENT,fecha_emision TEXT NOT NULL,tipo_comprobante TEXT NOT NULL,
 serie TEXT NOT NULL,correlativo INTEGER NOT NULL,cliente_documento TEXT,cliente_nombre TEXT,
 subtotal NUMERIC NOT NULL,igv NUMERIC NOT NULL,total NUMERIC NOT NULL,metodo_pago TEXT NOT NULL,
 caja_turno_id INTEGER REFERENCES caja_turno(id),usuario_id INTEGER REFERENCES usuarios(id),
 estado TEXT NOT NULL DEFAULT 'COMPLETADA',UNIQUE(serie,correlativo)
);
CREATE INDEX ventas_fecha ON ventas(fecha_emision);
CREATE TABLE detalle_venta (
 id INTEGER PRIMARY KEY AUTOINCREMENT,venta_id INTEGER NOT NULL REFERENCES ventas(id) ON DELETE CASCADE,
 producto_id INTEGER NOT NULL REFERENCES productos(id),cantidad INTEGER NOT NULL CHECK(cantidad>0),
 precio_unitario NUMERIC NOT NULL,lote_id INTEGER REFERENCES lotes(id),subtotal NUMERIC NOT NULL
);
CREATE TABLE comprobantes_sunat (
 id INTEGER PRIMARY KEY AUTOINCREMENT,tipo_comprobante TEXT NOT NULL,serie TEXT NOT NULL,correlativo INTEGER NOT NULL,
 cliente_documento TEXT,cliente_nombre TEXT,total NUMERIC NOT NULL,estado_sunat TEXT NOT NULL,hash_cpe TEXT,
 fecha_emision TEXT NOT NULL,venta_id INTEGER UNIQUE REFERENCES ventas(id),subtotal NUMERIC,igv NUMERIC,
 metodo_pago TEXT,emisor_nombre TEXT,emisor_ruc TEXT,emisor_direccion TEXT,pdf_documento BLOB,
 UNIQUE(serie,correlativo)
);
CREATE TABLE comprobante_detalle (
 id INTEGER PRIMARY KEY AUTOINCREMENT,comprobante_id INTEGER NOT NULL REFERENCES comprobantes_sunat(id) ON DELETE CASCADE,
 descripcion TEXT NOT NULL,cantidad INTEGER NOT NULL,precio_unitario NUMERIC NOT NULL,importe NUMERIC NOT NULL
);
CREATE TABLE datos_empresa(id INTEGER PRIMARY KEY,razon_social TEXT NOT NULL,ruc TEXT NOT NULL DEFAULT '',direccion TEXT NOT NULL DEFAULT '');
INSERT INTO datos_empresa VALUES(1,'Empresa','','');
CREATE TABLE secuencias_comprobante(serie TEXT PRIMARY KEY,siguiente INTEGER NOT NULL);
INSERT INTO secuencias_comprobante VALUES('B001',1),('F001',1);
CREATE TABLE tipo_cambio (
 id INTEGER PRIMARY KEY AUTOINCREMENT,fecha TEXT NOT NULL UNIQUE,compra NUMERIC NOT NULL,venta NUMERIC NOT NULL,origen TEXT DEFAULT 'MANUAL'
);
CREATE TABLE historial_precio (
 id INTEGER PRIMARY KEY AUTOINCREMENT,producto_id INTEGER NOT NULL REFERENCES productos(id),tipo_cambio_anterior NUMERIC,
 precio_anterior NUMERIC,tipo_cambio_nuevo NUMERIC,precio_nuevo NUMERIC,diferencia NUMERIC,fecha_cambio TEXT NOT NULL,
 usuario_id INTEGER REFERENCES usuarios(id)
);
CREATE TABLE proveedores (
 id INTEGER PRIMARY KEY AUTOINCREMENT,ruc TEXT UNIQUE,razon_social TEXT NOT NULL,contacto TEXT,telefono TEXT,
 correo TEXT,direccion TEXT,pais TEXT DEFAULT 'Peru',estado TEXT NOT NULL DEFAULT 'ACTIVO',
 creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime'))
);
CREATE TABLE reposicion_config (
 producto_id INTEGER PRIMARY KEY REFERENCES productos(id),ventas_dia NUMERIC,dias_entrega INTEGER DEFAULT 30,
 stock_seguridad INTEGER DEFAULT 10,proveedor TEXT,proveedor_id INTEGER REFERENCES proveedores(id)
);
CREATE INDEX reposicion_config_proveedor ON reposicion_config(proveedor_id);
CREATE TABLE compras_dua (
 id INTEGER PRIMARY KEY AUTOINCREMENT,proveedor TEXT NOT NULL,ruc TEXT,numero_dua TEXT NOT NULL,fecha_ingreso TEXT NOT NULL,
 tipo_cambio NUMERIC NOT NULL,total_fob_usd NUMERIC NOT NULL,total_gastos_usd NUMERIC NOT NULL,
 total_landed_usd NUMERIC NOT NULL,total_landed_pen NUMERIC NOT NULL,estado TEXT NOT NULL DEFAULT 'REGISTRADA',
 creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),proveedor_id INTEGER REFERENCES proveedores(id)
);
CREATE TABLE detalle_compra_dua (
 id INTEGER PRIMARY KEY AUTOINCREMENT,compra_id INTEGER NOT NULL REFERENCES compras_dua(id) ON DELETE CASCADE,
 producto_id INTEGER NOT NULL REFERENCES productos(id),lote_id INTEGER NOT NULL REFERENCES lotes(id),
 sku TEXT NOT NULL,descripcion TEXT NOT NULL,codigo_barras TEXT,codigo_lote TEXT NOT NULL,ubicacion TEXT,cantidad INTEGER NOT NULL,
 costo_fob_usd NUMERIC NOT NULL,gastos_dua_usd NUMERIC NOT NULL,costo_landed_usd NUMERIC NOT NULL,costo_landed_pen NUMERIC NOT NULL,
 margen NUMERIC NOT NULL,precio_sugerido_pen NUMERIC NOT NULL
);
CREATE TABLE ubicaciones_almacen (
 id INTEGER PRIMARY KEY AUTOINCREMENT,codigo TEXT NOT NULL UNIQUE,pasillo TEXT NOT NULL,anaquel INTEGER NOT NULL,
 nivel INTEGER NOT NULL,activo INTEGER NOT NULL DEFAULT 1,creado_en TEXT DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime'))
);
CREATE TABLE movimientos_stock (
 id INTEGER PRIMARY KEY AUTOINCREMENT,producto_id INTEGER NOT NULL REFERENCES productos(id),lote_id INTEGER REFERENCES lotes(id),
 cantidad INTEGER NOT NULL,motivo TEXT NOT NULL,usuario_id INTEGER REFERENCES usuarios(id),
 fecha TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime'))
);
CREATE INDEX movimientos_stock_producto ON movimientos_stock(producto_id,fecha,id);
CREATE TABLE pedidos_compra (
 id INTEGER PRIMARY KEY AUTOINCREMENT,proveedor_id INTEGER REFERENCES proveedores(id),proveedor_nombre TEXT NOT NULL,
 proveedor_ruc TEXT NOT NULL DEFAULT '',proveedor_contacto TEXT NOT NULL DEFAULT '',empresa_nombre TEXT NOT NULL,
 empresa_ruc TEXT NOT NULL DEFAULT '',empresa_direccion TEXT NOT NULL DEFAULT '',
 fecha TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),estado TEXT NOT NULL DEFAULT 'PENDIENTE',
 observaciones TEXT NOT NULL DEFAULT '',total_usd NUMERIC NOT NULL DEFAULT 0,total_pen NUMERIC NOT NULL DEFAULT 0,
 pdf_documento BLOB,excel_documento BLOB,compra_id INTEGER UNIQUE REFERENCES compras_dua(id),recibido_en TEXT,
 recibido_por INTEGER REFERENCES usuarios(id),
 CHECK((compra_id IS NULL AND recibido_en IS NULL) OR (compra_id IS NOT NULL AND recibido_en IS NOT NULL AND estado='RECIBIDO'))
);
CREATE TABLE pedidos_reposicion (
 id INTEGER PRIMARY KEY AUTOINCREMENT,producto_id INTEGER NOT NULL REFERENCES productos(id),cantidad INTEGER NOT NULL,
 proveedor TEXT,costo_usd NUMERIC,costo_pen NUMERIC,estado TEXT NOT NULL DEFAULT 'PENDIENTE',
 creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),
 pedido_compra_id INTEGER REFERENCES pedidos_compra(id),producto_nombre TEXT,producto_sku TEXT,unitario_usd NUMERIC,unitario_pen NUMERIC
);
CREATE INDEX pedidos_compra_detalle ON pedidos_reposicion(pedido_compra_id);
CREATE TABLE documentos_dua (
 id INTEGER PRIMARY KEY AUTOINCREMENT,referencia TEXT NOT NULL,compra_id INTEGER UNIQUE REFERENCES compras_dua(id),
 creado_por INTEGER REFERENCES usuarios(id) ON DELETE SET NULL,creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime'))
);
CREATE TABLE archivos_dua (
 id INTEGER PRIMARY KEY AUTOINCREMENT,documento_id INTEGER NOT NULL REFERENCES documentos_dua(id) ON DELETE CASCADE,
 nombre TEXT NOT NULL,tipo TEXT NOT NULL CHECK(tipo IN ('application/pdf','image/jpeg','image/png')),
 contenido BLOB NOT NULL CHECK(length(contenido) BETWEEN 1 AND 10485760),sha256 TEXT NOT NULL,
 creado_por INTEGER REFERENCES usuarios(id) ON DELETE SET NULL,creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),
 UNIQUE(documento_id,sha256)
);
CREATE INDEX archivos_dua_documento ON archivos_dua(documento_id,id);
CREATE TABLE borradores_dua (
 id INTEGER PRIMARY KEY AUTOINCREMENT,proveedor_id INTEGER NOT NULL REFERENCES proveedores(id),
 numero_dua TEXT NOT NULL CHECK(length(trim(numero_dua))>0),fecha TEXT NOT NULL,tipo_cambio NUMERIC NOT NULL CHECK(tipo_cambio>0),
 estado TEXT NOT NULL DEFAULT 'PENDIENTE' CHECK(estado IN ('PENDIENTE','APROBADA')),version INTEGER NOT NULL DEFAULT 0,
 documento_id INTEGER UNIQUE REFERENCES documentos_dua(id),compra_id INTEGER UNIQUE REFERENCES compras_dua(id),
 creado_por INTEGER REFERENCES usuarios(id) ON DELETE SET NULL,actualizado_por INTEGER REFERENCES usuarios(id) ON DELETE SET NULL,
 aprobado_por INTEGER REFERENCES usuarios(id) ON DELETE SET NULL,
 creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),
 actualizado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),aprobado_en TEXT,
 CHECK((estado='PENDIENTE' AND compra_id IS NULL AND aprobado_en IS NULL) OR (estado='APROBADA' AND compra_id IS NOT NULL AND aprobado_en IS NOT NULL))
);
CREATE UNIQUE INDEX borradores_dua_proveedor_numero ON borradores_dua(proveedor_id,lower(trim(numero_dua)));
CREATE TABLE lineas_borrador_dua (
 id INTEGER PRIMARY KEY AUTOINCREMENT,borrador_id INTEGER NOT NULL REFERENCES borradores_dua(id) ON DELETE CASCADE,
 sku TEXT NOT NULL,descripcion TEXT NOT NULL,codigo_barras TEXT,lote TEXT NOT NULL,ubicacion TEXT NOT NULL,
 cantidad INTEGER NOT NULL CHECK(cantidad>0),fob_usd NUMERIC NOT NULL CHECK(fob_usd>=0),
 gastos_usd NUMERIC NOT NULL CHECK(gastos_usd>=0),margen NUMERIC NOT NULL CHECK(margen>=0)
);
CREATE INDEX lineas_borrador_dua_cabecera ON lineas_borrador_dua(borrador_id,id);
CREATE TABLE motos_importadas (
 id INTEGER PRIMARY KEY AUTOINCREMENT,detalle_compra_id INTEGER NOT NULL REFERENCES detalle_compra_dua(id),
 vin TEXT NOT NULL UNIQUE CHECK(vin=upper(trim(vin))),motor TEXT NOT NULL,marca TEXT NOT NULL,modelo TEXT NOT NULL,
 anio INTEGER NOT NULL CHECK(anio BETWEEN 1900 AND 2100),color TEXT NOT NULL,placa TEXT NOT NULL DEFAULT '',
 partida TEXT NOT NULL DEFAULT '',titular_documento TEXT NOT NULL DEFAULT '',titular_nombre TEXT NOT NULL DEFAULT '',
 version INTEGER NOT NULL DEFAULT 0,creado_por INTEGER NOT NULL REFERENCES usuarios(id),
 creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime'))
);
CREATE UNIQUE INDEX motos_placa_unica ON motos_importadas(placa) WHERE placa<>'';
CREATE TABLE expedientes_sunarp (
 id INTEGER PRIMARY KEY AUTOINCREMENT,moto_id INTEGER NOT NULL REFERENCES motos_importadas(id),
 tipo TEXT NOT NULL CHECK(tipo IN ('PRIMERA_INSCRIPCION','TRANSFERENCIA')),
 estado TEXT NOT NULL DEFAULT 'BORRADOR' CHECK(estado IN ('BORRADOR','PREPARADO','PRESENTADO','OBSERVADO','INSCRITO','CANCELADO')),
 propietario_documento TEXT NOT NULL,propietario_nombre TEXT NOT NULL,vendedor_documento TEXT NOT NULL DEFAULT '',
 vendedor_nombre TEXT NOT NULL DEFAULT '',titulo TEXT NOT NULL DEFAULT '',anio_titulo INTEGER,oficina TEXT NOT NULL DEFAULT '',
 responsable TEXT NOT NULL DEFAULT '',observaciones TEXT NOT NULL DEFAULT '',revision_documental INTEGER NOT NULL DEFAULT 0,
 version INTEGER NOT NULL DEFAULT 0,creado_por INTEGER NOT NULL REFERENCES usuarios(id),
 creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),
 actualizado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),
 placa_resultado TEXT NOT NULL DEFAULT '',partida_resultado TEXT NOT NULL DEFAULT ''
);
CREATE UNIQUE INDEX un_tramite_activo_por_moto ON expedientes_sunarp(moto_id) WHERE estado NOT IN ('INSCRITO','CANCELADO');
CREATE UNIQUE INDEX una_primera_inscripcion ON expedientes_sunarp(moto_id) WHERE tipo='PRIMERA_INSCRIPCION' AND estado<>'CANCELADO';
CREATE TABLE archivos_sunarp (
 id INTEGER PRIMARY KEY AUTOINCREMENT,expediente_id INTEGER NOT NULL REFERENCES expedientes_sunarp(id),categoria TEXT NOT NULL,
 nombre TEXT NOT NULL,tipo TEXT NOT NULL,contenido BLOB NOT NULL CHECK(length(contenido) BETWEEN 1 AND 10485760),
 huella TEXT NOT NULL,creado_por INTEGER NOT NULL REFERENCES usuarios(id),
 creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime')),UNIQUE(expediente_id,huella)
);
CREATE TABLE historial_sunarp (
 id INTEGER PRIMARY KEY AUTOINCREMENT,moto_id INTEGER NOT NULL REFERENCES motos_importadas(id),
 expediente_id INTEGER REFERENCES expedientes_sunarp(id),usuario_id INTEGER NOT NULL REFERENCES usuarios(id),
 accion TEXT NOT NULL,detalle TEXT NOT NULL,fecha TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f','now','localtime'))
);
