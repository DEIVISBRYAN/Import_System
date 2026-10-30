-- Esquema Relacional Base para SQLite
CREATE TABLE IF NOT EXISTS productos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo_barras TEXT UNIQUE,
    sku TEXT NOT NULL,
    descripcion TEXT NOT NULL,
    stock_actual INTEGER NOT NULL DEFAULT 0,
    stock_minimo INTEGER NOT NULL DEFAULT 5,
    precio_venta_pen REAL NOT NULL,
    costo_promedio_pen REAL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS lotes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id INTEGER NOT NULL,
    codigo_lote TEXT NOT NULL,
    numero_dua TEXT,
    cantidad_inicial INTEGER NOT NULL,
    cantidad_disponible INTEGER NOT NULL,
    costo_unitario_usd REAL,
    costo_unitario_pen REAL NOT NULL,
    fecha_ingreso TEXT NOT NULL,
    fecha_vencimiento TEXT,
    FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS comprobantes_sunat (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tipo_comprobante TEXT NOT NULL,
    serie TEXT NOT NULL,
    correlativo INTEGER NOT NULL,
    cliente_documento TEXT,
    cliente_nombre TEXT,
    total REAL NOT NULL,
    estado_sunat TEXT NOT NULL,
    hash_cpe TEXT,
    fecha_emision TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tipo_cambio (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    fecha TEXT NOT NULL UNIQUE,
    compra REAL NOT NULL,
    venta REAL NOT NULL,
    origen TEXT DEFAULT 'SUNAT'
);
