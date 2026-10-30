package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.service.DBQPSqlDialect;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlDialectTest {
    @Test void adaptaSoloLosFragmentosDeCodigoSql() {
        String input="SELECT 'FOR UPDATE ILIKE now()',octet_length(contenido) FROM tabla WHERE nombre ILIKE ? FOR NO KEY UPDATE";
        String output=DBQPSqlDialect.sqlite(input);
        assertTrue(output.contains("'FOR UPDATE ILIKE now()'"));
        assertTrue(output.contains("length(contenido)"));
        assertTrue(output.contains("nombre LIKE ?"));
        assertFalse(output.contains("FOR NO KEY UPDATE"));
        assertEquals("SELECT 1",DBQPSqlDialect.sqlite("SELECT pg_advisory_xact_lock(74021, 2)"));
        assertEquals("SELECT * FROM tabla WHERE id=?",DBQPSqlDialect.sqlite("SELECT * FROM tabla WHERE id=? FOR UPDATE OF tabla"));
    }
    @Test void conservaParametrosYReturning() {
        String sql=DBQPSqlDialect.sqlite("UPDATE tabla SET fecha=localtimestamp WHERE id=? RETURNING id");
        assertTrue(sql.contains("strftime("));assertTrue(sql.contains("WHERE id=? RETURNING id"));
    }
}
