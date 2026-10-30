package com.ejemplo.crudpersona;

/**
 * Launcher desacoplado para evitar el error "JavaFX runtime components are missing"
 * al delegar la ejecución a CrudPersonaApplication sin extender de Application.
 *
 * @author D.B.Q.P.
 * @author R.P.L.C.
 * @author E.R.Q.Z.
 * @author D.A.M.M.
 */
public class Launcher {

    public static void main(String[] args) {
        CrudPersonaApplication.main(args);
    }
}
