CREATE TABLE movimientos_stock (
 id BIGSERIAL PRIMARY KEY,
 producto_id BIGINT NOT NULL REFERENCES productos(id),
 lote_id BIGINT REFERENCES lotes(id),
 cantidad INTEGER NOT NULL,
 motivo TEXT NOT NULL,
 usuario_id BIGINT REFERENCES usuarios(id),
 fecha TIMESTAMP NOT NULL DEFAULT localtimestamp
);
CREATE INDEX movimientos_stock_producto ON movimientos_stock(producto_id, fecha, id);
