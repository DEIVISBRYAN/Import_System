CREATE TABLE pedidos_compra (
 id BIGSERIAL PRIMARY KEY,
 proveedor_id BIGINT REFERENCES proveedores(id),
 proveedor_nombre TEXT NOT NULL, proveedor_ruc TEXT NOT NULL DEFAULT '', proveedor_contacto TEXT NOT NULL DEFAULT '',
 empresa_nombre TEXT NOT NULL, empresa_ruc TEXT NOT NULL DEFAULT '', empresa_direccion TEXT NOT NULL DEFAULT '',
 fecha TEXT NOT NULL DEFAULT to_char(localtimestamp, 'YYYY-MM-DD HH24:MI:SS'),
 estado TEXT NOT NULL DEFAULT 'PENDIENTE', observaciones TEXT NOT NULL DEFAULT '',
 total_usd NUMERIC NOT NULL DEFAULT 0, total_pen NUMERIC NOT NULL DEFAULT 0,
 pdf_documento BYTEA, excel_documento BYTEA
);
ALTER TABLE pedidos_reposicion ADD COLUMN pedido_compra_id BIGINT REFERENCES pedidos_compra(id);
ALTER TABLE pedidos_reposicion ADD COLUMN producto_nombre TEXT;
ALTER TABLE pedidos_reposicion ADD COLUMN producto_sku TEXT;
ALTER TABLE pedidos_reposicion ADD COLUMN unitario_usd NUMERIC;
ALTER TABLE pedidos_reposicion ADD COLUMN unitario_pen NUMERIC;
CREATE INDEX pedidos_compra_detalle ON pedidos_reposicion(pedido_compra_id);

-- Each legacy request keeps its original date and quantity; unrelated dates are not merged.
DO $$
DECLARE linea RECORD; cabecera BIGINT;
BEGIN
 FOR linea IN SELECT r.*, p.descripcion, p.sku FROM pedidos_reposicion r JOIN productos p ON p.id=r.producto_id LOOP
  INSERT INTO pedidos_compra(proveedor_id,proveedor_nombre,empresa_nombre,empresa_ruc,empresa_direccion,fecha,estado,total_usd,total_pen)
  SELECT (SELECT min(id) FROM proveedores WHERE lower(trim(razon_social))=lower(trim(linea.proveedor)) HAVING count(*)=1),
    COALESCE(linea.proveedor,'Sin proveedor'), razon_social,ruc,direccion,linea.creado_en,linea.estado,
    COALESCE(linea.costo_usd,0),COALESCE(linea.costo_pen,0) FROM datos_empresa WHERE id=1 RETURNING id INTO cabecera;
  UPDATE pedidos_reposicion SET pedido_compra_id=cabecera,producto_nombre=linea.descripcion,producto_sku=linea.sku,
    unitario_usd=round(COALESCE(linea.costo_usd,0)/NULLIF(linea.cantidad,0),6),
    unitario_pen=round(COALESCE(linea.costo_pen,0)/NULLIF(linea.cantidad,0),6) WHERE id=linea.id;
 END LOOP;
END $$;
