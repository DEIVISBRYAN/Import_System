package com.ejemplo.crudpersona.model;

public enum Rol {
    ADMINISTRADOR("Administrador Principal"),
    CAJERO("Cajero de Punto de Venta"),
    ALMACENERO("Responsable de Almacén e Inventario"),
    GERENTE("Gerente de Operaciones y Ventas"),
    CONTADOR("Contador y Cumplimiento SUNAT");

    private final String titulo;

    Rol(String titulo) {
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }

    @Override
    public String toString() {
        return titulo;
    }
}
