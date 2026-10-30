CREATE TABLE motos_importadas (
 id BIGSERIAL PRIMARY KEY,detalle_compra_id BIGINT NOT NULL REFERENCES detalle_compra_dua(id),
 vin VARCHAR(30) NOT NULL UNIQUE CHECK(vin=upper(trim(vin))),motor VARCHAR(50) NOT NULL,
 marca VARCHAR(80) NOT NULL,modelo VARCHAR(100) NOT NULL,anio INTEGER NOT NULL CHECK(anio BETWEEN 1900 AND 2100),
 color VARCHAR(60) NOT NULL,placa VARCHAR(20) NOT NULL DEFAULT '',partida VARCHAR(50) NOT NULL DEFAULT '',
 titular_documento VARCHAR(20) NOT NULL DEFAULT '',titular_nombre VARCHAR(150) NOT NULL DEFAULT '',
 version INTEGER NOT NULL DEFAULT 0,creado_por BIGINT NOT NULL REFERENCES usuarios(id),creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX motos_placa_unica ON motos_importadas(placa) WHERE placa<>'';
CREATE TABLE expedientes_sunarp (
 id BIGSERIAL PRIMARY KEY,moto_id BIGINT NOT NULL REFERENCES motos_importadas(id),
 tipo VARCHAR(25) NOT NULL CHECK(tipo IN ('PRIMERA_INSCRIPCION','TRANSFERENCIA')),
 estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR' CHECK(estado IN ('BORRADOR','PREPARADO','PRESENTADO','OBSERVADO','INSCRITO','CANCELADO')),
 propietario_documento VARCHAR(20) NOT NULL,propietario_nombre VARCHAR(150) NOT NULL,
 vendedor_documento VARCHAR(20) NOT NULL DEFAULT '',vendedor_nombre VARCHAR(150) NOT NULL DEFAULT '',
 titulo VARCHAR(50) NOT NULL DEFAULT '',anio_titulo INTEGER,oficina VARCHAR(120) NOT NULL DEFAULT '',
 responsable VARCHAR(150) NOT NULL DEFAULT '',observaciones VARCHAR(2000) NOT NULL DEFAULT '',
 revision_documental BOOLEAN NOT NULL DEFAULT FALSE,version INTEGER NOT NULL DEFAULT 0,
 creado_por BIGINT NOT NULL REFERENCES usuarios(id),creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX un_tramite_activo_por_moto ON expedientes_sunarp(moto_id) WHERE estado NOT IN ('INSCRITO','CANCELADO');
CREATE UNIQUE INDEX una_primera_inscripcion ON expedientes_sunarp(moto_id) WHERE tipo='PRIMERA_INSCRIPCION' AND estado<>'CANCELADO';
CREATE TABLE archivos_sunarp (
 id BIGSERIAL PRIMARY KEY,expediente_id BIGINT NOT NULL REFERENCES expedientes_sunarp(id),
 categoria VARCHAR(40) NOT NULL,nombre VARCHAR(180) NOT NULL,tipo VARCHAR(50) NOT NULL,contenido BYTEA NOT NULL,
 huella VARCHAR(64) NOT NULL,creado_por BIGINT NOT NULL REFERENCES usuarios(id),creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(expediente_id,huella),CHECK(octet_length(contenido) BETWEEN 1 AND 10485760)
);
CREATE TABLE historial_sunarp (
 id BIGSERIAL PRIMARY KEY,moto_id BIGINT NOT NULL REFERENCES motos_importadas(id),expediente_id BIGINT REFERENCES expedientes_sunarp(id),
 usuario_id BIGINT NOT NULL REFERENCES usuarios(id),accion VARCHAR(60) NOT NULL,detalle VARCHAR(2000) NOT NULL,
 fecha TIMESTAMPTZ NOT NULL DEFAULT now()
);
