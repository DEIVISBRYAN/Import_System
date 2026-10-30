ALTER TABLE pedidos_compra ADD COLUMN compra_id BIGINT UNIQUE REFERENCES compras_dua(id);
ALTER TABLE pedidos_compra ADD COLUMN recibido_en TIMESTAMP;
ALTER TABLE pedidos_compra ADD COLUMN recibido_por BIGINT REFERENCES usuarios(id);
ALTER TABLE pedidos_compra ADD CONSTRAINT recepcion_compra_consistente CHECK (
 (compra_id IS NULL AND recibido_en IS NULL) OR
 (compra_id IS NOT NULL AND recibido_en IS NOT NULL AND estado='RECIBIDO')
);
