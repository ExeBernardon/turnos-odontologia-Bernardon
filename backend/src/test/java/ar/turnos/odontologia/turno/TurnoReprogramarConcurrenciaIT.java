package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ar.turnos.odontologia.disponibilidad.DisponibilidadService;
import ar.turnos.odontologia.disponibilidad.ModoDisponibilidad;
import ar.turnos.odontologia.prestacion.Prestacion;

/** Dos reprogramaciones al mismo horario: la restricción EXCLUDE decide quién se lo queda. */
class TurnoReprogramarConcurrenciaIT extends TurnosApiITBase {

    private static final Instant DESTINO = en(48);

    @MockitoBean
    private DisponibilidadService disponibilidad;

    @BeforeEach
    void sincronizarValidacion() throws Exception {
        CountDownLatch llegaron = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);
        doAnswer(invocation -> {
            llegaron.countDown();
            if (llegaron.getCount() == 0) {
                salida.countDown();
            }
            salida.await(30, TimeUnit.SECONDS);
            return null;
        }).when(disponibilidad).validarHorario(any(Prestacion.class), any(Instant.class),
                any(ModoDisponibilidad.class), any(Long.class));
    }

    @Test
    void dosReprogramacionesSimultaneasTerminanEn200Y409ConUnSoloTurnoActivo() throws Exception {
        Turno primero = guardar(en(30), EstadoTurno.PENDIENTE, "repro-conc-1");
        Turno segundo = guardar(en(31), EstadoTurno.PENDIENTE, "repro-conc-2");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<Respuesta> tareaUno = () -> mover(primero.getId());
        Callable<Respuesta> tareaDos = () -> mover(segundo.getId());

        try {
            Future<Respuesta> r1 = pool.submit(tareaUno);
            Future<Respuesta> r2 = pool.submit(tareaDos);

            Respuesta respuestaUno = r1.get(60, TimeUnit.SECONDS);
            Respuesta respuestaDos = r2.get(60, TimeUnit.SECONDS);

            assertThat(List.of(respuestaUno.estado(), respuestaDos.estado()))
                    .containsExactlyInAnyOrder(200, 409);
            Respuesta perdedora = respuestaUno.estado() == 409 ? respuestaUno : respuestaDos;
            assertThat(json.readTree(perdedora.cuerpo()).get("codigo").asText())
                    .isEqualTo("HORARIO_OCUPADO");
        } finally {
            pool.shutdownNow();
        }

        List<Turno> enDestino = turnos
                .findByInicioGreaterThanEqualAndInicioLessThanOrderByInicioAsc(DESTINO, DESTINO.plusSeconds(1));
        assertThat(enDestino).hasSize(1);
        assertThat(enDestino.get(0).getEstado()).isIn(EstadoTurno.ACTIVOS);
        assertThat(turnos.count()).isEqualTo(2);
    }

    private Respuesta mover(Long id) throws Exception {
        var response = autenticado(patch("/api/turnos/{id}/reprogramar", id)
                .contentType(jsonType())
                .content("{\"inicio\":\"" + local(DESTINO) + "\"}"))
                .andReturn().getResponse();
        return new Respuesta(response.getStatus(), response.getContentAsString());
    }

    private record Respuesta(int estado, String cuerpo) {
    }
}
