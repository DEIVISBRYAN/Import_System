/**
 * Paquete marcador para los scripts SQL de migración de Flyway.
 * Este archivo es necesario para que JPMS (Java Platform Module System)
 * permita a Flyway (módulo sin nombre) leer los archivos .sql de este paquete
 * a través de ClassLoader.getResourceAsStream().
 */
package db.migration;
