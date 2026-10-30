-- Inserción de Tipo de Cambio Inicial (SBS / SUNAT)
INSERT OR IGNORE INTO tipo_cambio (fecha, compra, venta, origen) VALUES
('2026-10-09', 3.745, 3.755, 'SUNAT');

-- Inserción de Productos del Catálogo Importador
INSERT OR IGNORE INTO productos (id, codigo_barras, sku, descripcion, stock_actual, stock_minimo, precio_venta_pen, costo_promedio_pen) VALUES
(1, '7750001', 'AUD-BTX7', 'Audífonos BT X7 Bluetooth Canc. Ruido', 15, 5, 22.10, 16.88),
(2, '7750002', 'CRG-USBC65', 'Cargador USB-C 65W GaN Carga Rápida', 18, 6, 45.00, 33.75),
(3, '7750003', 'SMW-FITPRO', 'Smartwatch FitPro AMOLED GPS', 10, 4, 89.90, 67.50),
(4, '7750004', 'CBL-BRAID2M', 'Cable Tipo C a C Trenzado Reforzado 2M', 40, 10, 12.50, 7.80),
(5, '7750005', 'HUB-7IN1', 'Hub USB-C 7 en 1 HDMI 4K PD 100W', 8, 3, 75.00, 52.00);

-- Inserción de Lotes FIFO con DUA y Fechas de Ingreso
INSERT OR IGNORE INTO lotes (id, producto_id, codigo_lote, numero_dua, cantidad_inicial, cantidad_disponible, costo_unitario_usd, costo_unitario_pen, fecha_ingreso, fecha_vencimiento) VALUES
(1, 1, 'L-2409', '118-2026-10-003120', 20, 5, 4.50, 16.88, '2026-08-10', '2028-12-31'),
(2, 1, 'L-2410', '118-2026-10-007890', 25, 10, 5.00, 18.75, '2026-09-15', '2028-12-31'),
(3, 2, 'L-2411', '118-2026-10-008102', 20, 18, 9.00, 33.75, '2026-09-20', '2029-01-01'),
(4, 3, 'L-2408', '118-2026-10-002145', 15, 10, 18.00, 67.50, '2026-08-01', '2028-06-30'),
(5, 4, 'L-2412', '118-2026-10-009450', 50, 40, 2.08, 7.80, '2026-09-25', '2030-12-31'),
(6, 5, 'L-2413', '118-2026-10-009800', 10, 8, 13.87, 52.00, '2026-09-28', '2029-12-31');
