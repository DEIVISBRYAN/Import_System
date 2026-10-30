package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.model.TipoCambio;
import com.ejemplo.crudpersona.repository.*;
import com.ejemplo.crudpersona.service.*;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TipoCambioManualTest {
    @Test void guardarManualConservaPreciosYPublicaActualizacion(){
        var repo=mock(TipoCambioRepository.class);var productos=mock(ProductoRepository.class);var historial=mock(HistorialPrecioRepository.class);var eventos=mock(ApplicationEventPublisher.class);
        when(repo.findTopByOrderByFechaDesc()).thenReturn(Optional.of(TipoCambio.builder().venta(new BigDecimal("3.75")).build()));
        when(repo.findByFecha(any())).thenReturn(Optional.empty());
        var service=new DBQPTipoCambioService(repo,productos,historial,mock(JdbcTemplate.class),eventos);
        service.guardarManual(new BigDecimal("3.75"),new BigDecimal("3.80"));
        verify(repo).save(argThat(c->"MANUAL".equals(c.getOrigen())&&c.getVenta().compareTo(new BigDecimal("3.80"))==0));
        verifyNoInteractions(productos,historial);verify(eventos).publishEvent(any(ERQZTipoCambioActualizado.class));
    }
    @Test void tasaInvalidaNoLlegaALaBase(){
        var db=mock(JdbcTemplate.class);var repo=mock(TipoCambioRepository.class);
        var service=new DBQPTipoCambioService(repo,mock(ProductoRepository.class),mock(HistorialPrecioRepository.class),db,mock(ApplicationEventPublisher.class));
        for(BigDecimal rate:new BigDecimal[]{null,BigDecimal.ZERO,new BigDecimal("-1"),new BigDecimal("100"),new BigDecimal("3.12345")})assertThrows(IllegalArgumentException.class,()->service.guardarManual(BigDecimal.ONE,rate));
        verifyNoInteractions(repo,db);
    }
    @Test void escritorioNoReferenciaLogoNiApi()throws Exception{
        for(String file:new String[]{"login-view.fxml","main-view.fxml"})assertFalse(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/view",file)).contains("grupo-festa-logo"));
        String cambio=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/view/tipo-cambio-view.fxml"));
        assertFalse(cambio.contains("ActualizarApi"));assertTrue(cambio.contains("onGuardarManual"));
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of("src/main/java/com/ejemplo/crudpersona/service/BcrpTipoCambioClient.java")));
    }
}
