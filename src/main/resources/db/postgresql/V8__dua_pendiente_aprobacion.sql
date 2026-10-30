CREATE TABLE borradores_dua (
    id BIGSERIAL PRIMARY KEY,
    proveedor_id BIGINT NOT NULL REFERENCES proveedores(id),
    numero_dua TEXT NOT NULL CHECK (length(trim(numero_dua)) > 0),
    fecha DATE NOT NULL,
    tipo_cambio NUMERIC NOT NULL CHECK (tipo_cambio > 0),
    estado TEXT NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','APROBADA')),
    version INTEGER NOT NULL DEFAULT 0,
    documento_id BIGINT UNIQUE REFERENCES documentos_dua(id),
    compra_id BIGINT UNIQUE REFERENCES compras_dua(id),
    creado_por BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    actualizado_por BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    aprobado_por BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT localtimestamp,
    actualizado_en TIMESTAMP NOT NULL DEFAULT localtimestamp,
    aprobado_en TIMESTAMP,
    CHECK ((estado='PENDIENTE' AND compra_id IS NULL AND aprobado_en IS NULL)
        OR (estado='APROBADA' AND compra_id IS NOT NULL AND aprobado_en IS NOT NULL))
);
CREATE UNIQUE INDEX borradores_dua_proveedor_numero ON borradores_dua(proveedor_id,lower(trim(numero_dua)));

CREATE TABLE lineas_borrador_dua (
    id BIGSERIAL PRIMARY KEY,
    borrador_id BIGINT NOT NULL REFERENCES borradores_dua(id) ON DELETE CASCADE,
    sku TEXT NOT NULL,
    descripcion TEXT NOT NULL,
    codigo_barras TEXT,
    lote TEXT NOT NULL,
    ubicacion TEXT NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    fob_usd NUMERIC NOT NULL CHECK (fob_usd >= 0),
    gastos_usd NUMERIC NOT NULL CHECK (gastos_usd >= 0),
    margen NUMERIC NOT NULL CHECK (margen >= 0)
);
CREATE INDEX lineas_borrador_dua_cabecera ON lineas_borrador_dua(borrador_id,id);
