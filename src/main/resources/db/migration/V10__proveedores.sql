CREATE TABLE IF NOT EXISTS proveedores (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ruc TEXT UNIQUE,
    razon_social TEXT NOT NULL,
    contacto TEXT,
    telefono TEXT,
    correo TEXT,
    direccion TEXT,
    pais TEXT DEFAULT 'Perú',
    estado TEXT NOT NULL DEFAULT 'ACTIVO',
    creado_en TEXT NOT NULL DEFAULT (strftime('%Y-%m-%d %H:%M:%f', 'now', 'localtime'))
);

INSERT OR IGNORE INTO proveedores (ruc, razon_social, contacto, telefono, correo, direccion, pais, estado)
VALUES ('20554433221', 'Importadora del Perú S.A.C.', 'Carlos Mendoza', '999888777', 'compras@importadoraperu.pe', 'Lima', 'Perú', 'ACTIVO');

ALTER TABLE compras_dua ADD COLUMN proveedor_id INTEGER REFERENCES proveedores(id);

INSERT OR IGNORE INTO proveedores (ruc, razon_social, contacto, telefono, correo, direccion, pais, estado)
SELECT NULL, proveedor, '', '', '', '', 'Perú', 'ACTIVO'
FROM compras_dua
WHERE proveedor IS NOT NULL
  AND trim(proveedor) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM proveedores p WHERE lower(p.razon_social) = lower(compras_dua.proveedor)
  );

UPDATE compras_dua
SET proveedor_id = (
    SELECT p.id
    FROM proveedores p
    WHERE lower(p.razon_social) = lower(compras_dua.proveedor)
    LIMIT 1
)
WHERE proveedor_id IS NULL;
