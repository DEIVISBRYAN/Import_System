CREATE UNIQUE INDEX productos_sku_unico ON productos(lower(trim(sku)));
