CREATE TABLE IF NOT EXISTS compras_dua (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    proveedor TEXT NOT NULL,
    ruc TEXT,
    numero_dua TEXT NOT NULL,
    fecha_ingreso TEXT NOT NULL,
    tipo_cambio REAL NOT NULL,
    total_fob_usd REAL NOT NULL,
    total_gastos_usd REAL NOT NULL,
    total_landed_usd REAL NOT NULL,
    total_landed_pen REAL NOT NULL,
    estado TEXT NOT NULL DEFAULT 'REGISTRADA',
    creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f', 'now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS detalle_compra_dua (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    compra_id INTEGER NOT NULL,
    producto_id INTEGER NOT NULL,
    lote_id INTEGER NOT NULL,
    sku TEXT NOT NULL,
    descripcion TEXT NOT NULL,
    codigo_barras TEXT,
    codigo_lote TEXT NOT NULL,
    ubicacion TEXT,
    cantidad INTEGER NOT NULL,
    costo_fob_usd REAL NOT NULL,
    gastos_dua_usd REAL NOT NULL,
    costo_landed_usd REAL NOT NULL,
    costo_landed_pen REAL NOT NULL,
    margen REAL NOT NULL,
    precio_sugerido_pen REAL NOT NULL,
    FOREIGN KEY (compra_id) REFERENCES compras_dua(id) ON DELETE CASCADE,
    FOREIGN KEY (producto_id) REFERENCES productos(id),
    FOREIGN KEY (lote_id) REFERENCES lotes(id)
);
