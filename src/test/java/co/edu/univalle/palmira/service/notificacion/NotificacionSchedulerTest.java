package co.edu.univalle.palmira.service.notificacion;

import co.edu.univalle.palmira.config.NotificacionesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** HU-038: tarea programada de la cola de correos. */
@ExtendWith(MockitoExtension.class)
class NotificacionSchedulerTest {

    @Mock
    private NotificacionProcesador procesador;

    private NotificacionesProperties propiedades;
    private NotificacionScheduler scheduler;

    @BeforeEach
    void setUp() {
        propiedades = new NotificacionesProperties();
        scheduler = new NotificacionScheduler(procesador, propiedades);
    }

    @Test
    @DisplayName("Criterio 8: un error en un envío no detiene el resto de los envíos")
    void criterio8ErrorNoDetieneLosDemas() {
        when(procesador.enviarSiguiente())
                .thenThrow(new IllegalStateException("fallo de prueba"))
                .thenReturn(true, true, false);

        assertEquals(2, scheduler.enviarPendientes());
        verify(procesador, times(4)).enviarSiguiente();
    }

    @Test
    @DisplayName("Criterio 8: un error en un evento no detiene el resto de los eventos")
    void criterio8ErrorEnEventoNoDetieneLosDemas() {
        when(procesador.procesarSiguienteEvento())
                .thenThrow(new IllegalStateException("fallo de prueba"))
                .thenReturn(true, false);

        assertEquals(1, scheduler.procesarEventos());
        verify(procesador, times(3)).procesarSiguienteEvento();
    }

    @Test
    @DisplayName("Cada corrida procesa como máximo el lote configurado")
    void respetaElLote() {
        propiedades.setLote(3);
        when(procesador.enviarSiguiente()).thenReturn(true);

        assertEquals(3, scheduler.enviarPendientes());
        verify(procesador, times(3)).enviarSiguiente();
    }

    @Test
    @DisplayName("La corrida resuelve primero los eventos y después envía")
    void ordenDeLaCorrida() {
        when(procesador.procesarSiguienteEvento()).thenReturn(false);
        when(procesador.enviarSiguiente()).thenReturn(false);

        scheduler.ejecutar();

        var orden = inOrder(procesador);
        orden.verify(procesador).procesarSiguienteEvento();
        orden.verify(procesador).enviarSiguiente();
    }
}
