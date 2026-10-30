ALTER TABLE reposicion_config ADD COLUMN proveedor_id BIGINT REFERENCES proveedores(id);

-- Solo vincula nombres anteriores cuando identifican un unico proveedor.
UPDATE reposicion_config c
SET proveedor_id = p.id
FROM (
    SELECT lower(trim(razon_social)) nombre, min(id) id
    FROM proveedores GROUP BY lower(trim(razon_social)) HAVING count(*) = 1
) p
WHERE lower(trim(c.proveedor)) = p.nombre;

CREATE INDEX reposicion_config_proveedor ON reposicion_config(proveedor_id);
