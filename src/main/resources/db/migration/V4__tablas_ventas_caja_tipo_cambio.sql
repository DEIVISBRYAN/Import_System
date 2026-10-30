-- Tabla de Ventas
CREATE TABLE IF NOT EXISTS ventas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha_emision TEXT NOT NULL,
    tipo_comprobante TEXT NOT NULL,
    serie TEXT NOT NULL,
    correlativo INTEGER NOT NULL,
    cliente_documento TEXT,
    cliente_nombre TEXT,
    subtotal REAL NOT NULL,
    igv REAL NOT NULL,
    total REAL NOT NULL,
    metodo_pago TEXT NOT NULL,
    caja_turno_id INTEGER,
    usuario_id INTEGER,
    estado TEXT NOT NULL DEFAULT 'COMPLETADA'
);

-- Tabla de Detalle de Ventas
CREATE TABLE IF NOT EXISTS detalle_venta (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    venta_id INTEGER NOT NULL,
    producto_id INTEGER NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario REAL NOT NULL,
    lote_id INTEGER,
    subtotal REAL NOT NULL,
    FOREIGN KEY (venta_id) REFERENCES ventas(id) ON DELETE CASCADE,
    FOREIGN KEY (producto_id) REFERENCES productos(id)
);

-- Tabla de Turnos de Caja
CREATE TABLE IF NOT EXISTS caja_turno (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    numero_caja INTEGER NOT NULL,
    fecha_apertura TEXT NOT NULL,
    fecha_cierre TEXT,
    monto_apertura REAL NOT NULL,
    monto_efectivo_ventas REAL DEFAULT 0,
    monto_yape_plin REAL DEFAULT 0,
    monto_tarjeta REAL DEFAULT 0,
    monto_contado REAL,
    monto_esperado REAL,
    diferencia REAL,
    usuario_id INTEGER,
    estado TEXT NOT NULL DEFAULT 'ABIERTA'
);

-- Tabla de Historial de Precios
CREATE TABLE IF NOT EXISTS historial_precio (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id INTEGER NOT NULL,
    tipo_cambio_anterior REAL,
    precio_anterior REAL,
    tipo_cambio_nuevo REAL,
    precio_nuevo REAL,
    diferencia REAL,
    fecha_cambio TEXT NOT NULL,
    usuario_id INTEGER,
    FOREIGN KEY (producto_id) REFERENCES productos(id)
);
