ALTER TABLE comprobantes_sunat ADD COLUMN venta_id INTEGER REFERENCES ventas(id);
ALTER TABLE comprobantes_sunat ADD COLUMN subtotal REAL;
ALTER TABLE comprobantes_sunat ADD COLUMN igv REAL;
ALTER TABLE comprobantes_sunat ADD COLUMN metodo_pago TEXT;
ALTER TABLE comprobantes_sunat ADD COLUMN emisor_nombre TEXT;
ALTER TABLE comprobantes_sunat ADD COLUMN emisor_ruc TEXT;
ALTER TABLE comprobantes_sunat ADD COLUMN emisor_direccion TEXT;
ALTER TABLE comprobantes_sunat ADD COLUMN pdf_documento BLOB;
CREATE UNIQUE INDEX idx_comprobante_venta ON comprobantes_sunat(venta_id) WHERE venta_id IS NOT NULL;

CREATE TABLE comprobante_detalle (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    comprobante_id INTEGER NOT NULL REFERENCES comprobantes_sunat(id) ON DELETE CASCADE,
    descripcion TEXT NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario REAL NOT NULL,
    importe REAL NOT NULL
);
CREATE TABLE datos_empresa (
    id INTEGER PRIMARY KEY,
    razon_social TEXT NOT NULL,
    ruc TEXT NOT NULL DEFAULT '',
    direccion TEXT NOT NULL DEFAULT ''
);
INSERT INTO datos_empresa(id, razon_social, ruc, direccion) VALUES (1, 'Importadora Perú', '', '');

CREATE TABLE cajas (
    numero INTEGER PRIMARY KEY,
    nombre TEXT NOT NULL
);
INSERT INTO cajas(numero, nombre) VALUES (1, 'Caja 1'), (2, 'Caja 2'), (3, 'Caja 3');
CREATE TABLE secuencias_comprobante (
    serie TEXT PRIMARY KEY,
    siguiente INTEGER NOT NULL
);
INSERT INTO secuencias_comprobante(serie, siguiente)
SELECT 'B001', COALESCE(MAX(correlativo), 0) + 1 FROM ventas WHERE serie = 'B001';
INSERT INTO secuencias_comprobante(serie, siguiente)
SELECT 'F001', COALESCE(MAX(correlativo), 0) + 1 FROM ventas WHERE serie = 'F001';

UPDATE comprobantes_sunat SET estado_sunat = 'PENDIENTE', hash_cpe = NULL
WHERE estado_sunat = 'ACEPTADO' AND (hash_cpe LIKE 'LOCAL-%' OR hash_cpe = 'cE4u0Wp1fD9mZq9X2k5R8vL0==');
