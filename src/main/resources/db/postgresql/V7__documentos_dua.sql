CREATE TABLE documentos_dua (
    id BIGSERIAL PRIMARY KEY,
    referencia TEXT NOT NULL,
    compra_id BIGINT UNIQUE REFERENCES compras_dua(id),
    creado_por BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT localtimestamp
);

CREATE TABLE archivos_dua (
    id BIGSERIAL PRIMARY KEY,
    documento_id BIGINT NOT NULL REFERENCES documentos_dua(id) ON DELETE CASCADE,
    nombre TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('application/pdf', 'image/jpeg', 'image/png')),
    contenido BYTEA NOT NULL CHECK (octet_length(contenido) BETWEEN 1 AND 10485760),
    sha256 VARCHAR(64) NOT NULL,
    creado_por BIGINT REFERENCES usuarios(id) ON DELETE SET NULL,
    creado_en TIMESTAMP NOT NULL DEFAULT localtimestamp,
    UNIQUE (documento_id, sha256)
);

CREATE INDEX archivos_dua_documento ON archivos_dua(documento_id, id);
