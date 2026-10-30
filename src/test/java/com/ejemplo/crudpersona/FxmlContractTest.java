package com.ejemplo.crudpersona;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class FxmlContractTest {
    @Test void todosLosEventosTienenControladorYMetodo() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        int eventos = 0, vistas = 0;
        try (var files = Files.list(Path.of("src/main/resources/view"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                var doc = factory.newDocumentBuilder().parse(file.toFile());
                Element root = doc.getDocumentElement();
                Class<?> controller = Class.forName(root.getAttribute("fx:controller"));
                var elements = doc.getElementsByTagName("*");
                for (int i = 0; i < elements.getLength(); i++) {
                    var attrs = elements.item(i).getAttributes();
                    for (int j = 0; j < attrs.getLength(); j++) {
                        var attr = attrs.item(j);
                        if (attr.getNodeName().startsWith("on") && attr.getNodeValue().startsWith("#")) {
                            String handler = attr.getNodeValue().substring(1);
                            assertTrue(Arrays.stream(controller.getDeclaredMethods()).anyMatch(m -> m.getName().equals(handler)
                                            && m.getParameterCount() <= 1), file + ": falta " + handler);
                            eventos++;
                        }
                    }
                }
                vistas++;
            }
        }
        assertEquals(17, vistas);
        assertTrue(eventos > 90, "No se verificaron todos los eventos: " + eventos);
    }
}
