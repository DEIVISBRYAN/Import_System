module com.ejemplo.crudpersona {
    // Lombok (Requerido solo en tiempo de compilación)
    requires static lombok;

    // HikariCP (Para la conexión a la base de datos)
    requires com.zaxxer.hikari;

    // JavaFX 21
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;

    // AtlantaFX & Iconos Ikonli
    requires atlantafx.base;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.feather;

    // Spring Boot 3.3.4 & Contexto
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires spring.tx;
    requires spring.jdbc;
    requires spring.data.jpa;
    requires spring.data.commons;
    requires spring.security.crypto;

    // Jakarta Persistence & Hibernate
    requires jakarta.persistence;
    requires jakarta.annotation;
    // requires jakarta.validation; // Comentado para evitar java.lang.module.FindException
    requires org.hibernate.orm.core;

    // Conectividad de base de datos, AWT y Preferencias
    requires java.sql;
    requires java.net.http;
    requires org.postgresql.jdbc;
    requires java.desktop;
    requires java.prefs;

    // Permite a Flyway (en el classpath/módulo sin nombre) leer los SQL de migración
    // cuando la app corre como módulo nombrado (IntelliJ / --module-path)
    opens db.migration;
    opens db.postgresql;
    opens db.sqlite;

    // Librerías de utilidades
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires com.github.librepdf.openpdf;
    requires tabula;
    requires org.apache.pdfbox;

    // Apertura reflexiva para Spring Container, JavaFX FXML Loader, Hibernate ORM y HikariCP
    opens com.ejemplo.crudpersona to spring.core, spring.beans, spring.context;
    opens com.ejemplo.crudpersona.config to spring.core, spring.beans, spring.context, com.zaxxer.hikari;
    opens com.ejemplo.crudpersona.controller to javafx.fxml, spring.core, spring.beans;
    opens com.ejemplo.crudpersona.ui to javafx.fxml;
    opens com.ejemplo.crudpersona.model to org.hibernate.orm.core, spring.core;
    opens com.ejemplo.crudpersona.service to spring.core, spring.beans;
    opens com.ejemplo.crudpersona.repository to spring.core, spring.data.commons;

    // Exportación pública de paquetes modulares
    exports com.ejemplo.crudpersona;
    exports com.ejemplo.crudpersona.config;
    exports com.ejemplo.crudpersona.controller;
    exports com.ejemplo.crudpersona.ui;
    exports com.ejemplo.crudpersona.model;
    exports com.ejemplo.crudpersona.service;
    exports com.ejemplo.crudpersona.repository;
}
