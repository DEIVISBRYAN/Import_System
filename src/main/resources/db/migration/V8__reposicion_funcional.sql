CREATE TABLE IF NOT EXISTS reposicion_config (
    producto_id INTEGER PRIMARY KEY,
    ventas_dia REAL,
    dias_entrega INTEGER DEFAULT 30,
    stock_seguridad INTEGER DEFAULT 10,
    proveedor TEXT,
    FOREIGN KEY (producto_id) REFERENCES productos(id)
);

CREATE TABLE IF NOT EXISTS pedidos_reposicion (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    producto_id INTEGER NOT NULL,
    cantidad INTEGER NOT NULL,
    proveedor TEXT,
    costo_usd REAL,
    costo_pen REAL,
    estado TEXT NOT NULL DEFAULT 'PENDIENTE',
    creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f', 'now', 'localtime')),
    FOREIGN KEY (producto_id) REFERENCES productos(id)
);
