CREATE TABLE IF NOT EXISTS ubicaciones_almacen (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo TEXT NOT NULL UNIQUE,
    pasillo TEXT NOT NULL,
    anaquel INTEGER NOT NULL,
    nivel INTEGER NOT NULL,
    activo INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT DEFAULT (datetime('now', 'localtime'))
);

WITH RECURSIVE
    pasillos(p) AS (
        SELECT 'A' UNION ALL SELECT 'B' UNION ALL SELECT 'C'
    ),
    anaqueles(a) AS (
        SELECT 1 UNION ALL SELECT a + 1 FROM anaqueles WHERE a < 4
    ),
    niveles(n) AS (
        SELECT 1 UNION ALL SELECT n + 1 FROM niveles WHERE n < 3
    )
INSERT OR IGNORE INTO ubicaciones_almacen (codigo, pasillo, anaquel, nivel, activo)
SELECT p || '-' || printf('%02d', a) || '-' || n, p, a, n, 1
FROM pasillos, anaqueles, niveles;

INSERT OR IGNORE INTO ubicaciones_almacen (codigo, pasillo, anaquel, nivel, activo)
SELECT DISTINCT
    upper(trim(ubicacion)) AS codigo,
    substr(upper(trim(ubicacion)), 1, instr(upper(trim(ubicacion)), '-') - 1) AS pasillo,
    CAST(substr(upper(trim(ubicacion)), instr(upper(trim(ubicacion)), '-') + 1, 2) AS INTEGER) AS anaquel,
    CAST(substr(upper(trim(ubicacion)), length(upper(trim(ubicacion))), 1) AS INTEGER) AS nivel,
    1
FROM lotes
WHERE ubicacion IS NOT NULL
  AND trim(ubicacion) <> ''
  AND upper(trim(ubicacion)) LIKE '_-__-_';
