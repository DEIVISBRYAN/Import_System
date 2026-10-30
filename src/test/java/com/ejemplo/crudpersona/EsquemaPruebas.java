package com.ejemplo.crudpersona;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

final class EsquemaPruebas {
    private EsquemaPruebas() {}
    static void limpiar(JdbcTemplate db,String schema,String... tables){
        assertNotNull(schema);assertTrue(schema.matches("verificacion_(docs_)?[a-f0-9]{32}"),"Esquema de prueba no permitido");
        assertEquals(schema,db.queryForObject("SELECT current_schema()",String.class),"Conexion fuera del esquema temporal");
        assertEquals(List.of(schema),db.queryForList("SELECT unnest(current_schemas(false))::text",String.class),"La conexion permite consultar otros esquemas");
        assertEquals(0,db.queryForObject("SELECT count(*) FROM pg_constraint c JOIN pg_class origen ON origen.oid=c.conrelid JOIN pg_class destino ON destino.oid=c.confrelid WHERE c.contype='f' AND destino.relnamespace=to_regnamespace(?) AND origen.relnamespace<>to_regnamespace(?)",Integer.class,schema,schema),"Hay referencias externas al esquema de pruebas");
        for(String table:tables){assertTrue(table.matches("[a-z_]+"));assertNotNull(db.queryForObject("SELECT to_regclass(?)::text",String.class,schema+"."+table),"Falta una tabla temporal");}
        db.execute("TRUNCATE "+String.join(",",Arrays.stream(tables).map(t->schema+"."+t).toList())+" RESTART IDENTITY CASCADE");
    }
}
