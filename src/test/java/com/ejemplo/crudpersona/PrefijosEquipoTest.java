package com.ejemplo.crudpersona;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import static org.junit.jupiter.api.Assertions.*;

class PrefijosEquipoTest {
    private static final List<String> EQUIPO = List.of("DBQP", "RPLC", "ERQZ");

    @Test void nombresPublicosReferenciasYBeansSonCoherentes() throws Exception {
        var base = Path.of("src/main/java/com/ejemplo/crudpersona");
        var counts = new HashMap<String, Integer>();
        EQUIPO.forEach(p -> counts.put(p, 0));
        var beans = new HashSet<String>();
        try (var files = Files.walk(base)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = path.getFileName().toString().replaceFirst("\\.java$", "");
                String prefix = EQUIPO.stream().filter(name::startsWith).findFirst().orElse(null);
                if (prefix == null) continue;
                counts.merge(prefix, 1, Integer::sum);
                String relative = base.relativize(path).toString().replace('\\', '.').replace('/', '.');
                Class<?> type = Class.forName("com.ejemplo.crudpersona." + relative.replaceFirst("\\.java$", ""));
                assertEquals(name, type.getSimpleName(), path.toString());
                for (Class<?> contract : type.getInterfaces()) {
                    if (contract.getSimpleName().endsWith("ServiceI")) {
                        assertTrue(contract.getSimpleName().startsWith(prefix), type + " tiene otra autoria en su interfaz");
                    }
                }
                if (AnnotatedElementUtils.hasAnnotation(type, Component.class)) {
                    String bean = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(
                            new AnnotatedGenericBeanDefinition(type), new DefaultListableBeanFactory());
                    assertTrue(beans.add(bean), "Nombre de bean duplicado: " + bean);
                }
            }
        }
        assertEquals(Map.of("DBQP", 20, "RPLC", 20, "ERQZ", 20), counts);
        assertTrue(beans.size() > 30, "No se comprobaron los componentes de Spring");
    }
}
