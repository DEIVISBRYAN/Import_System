-- Tabla de usuarios con roles para SQLite
CREATE TABLE IF NOT EXISTS usuarios (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    nombre_completo TEXT NOT NULL,
    rol TEXT NOT NULL,
    activo INTEGER NOT NULL DEFAULT 1,
    creado_en TEXT DEFAULT (datetime('now', 'localtime'))
);

-- Semilla de usuarios del sistema con contraseñas legibles (admin123)
INSERT OR IGNORE INTO usuarios (username, password_hash, nombre_completo, rol, activo) VALUES
('admin',    'admin123', 'Carlos Mendoza Ramos', 'ADMINISTRADOR', 1),
('cajero',   'cajero123', 'Ana Torres Quispe',    'CAJERO',        1),
('almacen',  'almacen123', 'Jorge Luis Paredes',   'ALMACENERO',    1),
('gerente',  'gerente123', 'Eduardo Salazar Ruiz', 'GERENTE',       1),
('contador', 'contador123', 'Lucía Vargas Soto',    'CONTADOR',      1);
