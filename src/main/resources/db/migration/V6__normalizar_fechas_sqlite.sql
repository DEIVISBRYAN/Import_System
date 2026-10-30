UPDATE ventas
SET fecha_emision = CASE
    WHEN length(trim(fecha_emision)) > 10 THEN strftime('%Y-%m-%d %H:%M:%f', CAST(fecha_emision AS INTEGER) / 1000, 'unixepoch')
    ELSE strftime('%Y-%m-%d %H:%M:%f', CAST(fecha_emision AS INTEGER), 'unixepoch')
END
WHERE fecha_emision IS NOT NULL
  AND fecha_emision NOT LIKE '%-%'
  AND fecha_emision GLOB '[0-9]*';

UPDATE ventas
SET fecha_emision = fecha_emision || '.000'
WHERE fecha_emision IS NOT NULL
  AND fecha_emision LIKE '____-__-__ __:__:__'
  AND fecha_emision NOT LIKE '%.%';

UPDATE caja_turno
SET fecha_apertura = CASE
    WHEN length(trim(fecha_apertura)) > 10 THEN strftime('%Y-%m-%d %H:%M:%f', CAST(fecha_apertura AS INTEGER) / 1000, 'unixepoch')
    ELSE strftime('%Y-%m-%d %H:%M:%f', CAST(fecha_apertura AS INTEGER), 'unixepoch')
END
WHERE fecha_apertura IS NOT NULL
  AND fecha_apertura NOT LIKE '%-%'
  AND fecha_apertura GLOB '[0-9]*';

UPDATE caja_turno
SET fecha_apertura = fecha_apertura || '.000'
WHERE fecha_apertura IS NOT NULL
  AND fecha_apertura LIKE '____-__-__ __:__:__'
  AND fecha_apertura NOT LIKE '%.%';

UPDATE caja_turno
SET fecha_cierre = CASE
    WHEN length(trim(fecha_cierre)) > 10 THEN strftime('%Y-%m-%d %H:%M:%f', CAST(fecha_cierre AS INTEGER) / 1000, 'unixepoch')
    ELSE strftime('%Y-%m-%d %H:%M:%f', CAST(fecha_cierre AS INTEGER), 'unixepoch')
END
WHERE fecha_cierre IS NOT NULL
  AND fecha_cierre NOT LIKE '%-%'
  AND fecha_cierre GLOB '[0-9]*';

UPDATE caja_turno
SET fecha_cierre = fecha_cierre || '.000'
WHERE fecha_cierre IS NOT NULL
  AND fecha_cierre LIKE '____-__-__ __:__:__'
  AND fecha_cierre NOT LIKE '%.%';

UPDATE lotes
SET fecha_ingreso = fecha_ingreso || ' 00:00:00.000'
WHERE fecha_ingreso IS NOT NULL
  AND fecha_ingreso LIKE '____-__-__'
  AND length(trim(fecha_ingreso)) = 10;

UPDATE lotes
SET fecha_vencimiento = fecha_vencimiento || ' 00:00:00.000'
WHERE fecha_vencimiento IS NOT NULL
  AND fecha_vencimiento LIKE '____-__-__'
  AND length(trim(fecha_vencimiento)) = 10;

UPDATE tipo_cambio
SET fecha = fecha || ' 00:00:00.000'
WHERE fecha IS NOT NULL
  AND fecha LIKE '____-__-__'
  AND length(trim(fecha)) = 10;

UPDATE comprobantes_sunat
SET fecha_emision = fecha_emision || '.000'
WHERE fecha_emision IS NOT NULL
  AND fecha_emision LIKE '____-__-__ __:__:__'
  AND fecha_emision NOT LIKE '%.%';

UPDATE historial_precio
SET fecha_cambio = fecha_cambio || '.000'
WHERE fecha_cambio IS NOT NULL
  AND fecha_cambio LIKE '____-__-__ __:__:__'
  AND fecha_cambio NOT LIKE '%.%';

UPDATE usuarios
SET creado_en = creado_en || '.000'
WHERE creado_en IS NOT NULL
  AND creado_en LIKE '____-__-__ __:__:__'
  AND creado_en NOT LIKE '%.%';
