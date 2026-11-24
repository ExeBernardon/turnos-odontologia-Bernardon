package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import ar.turnos.odontologia.paciente.Paciente;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;

@ExtendWith(OutputCaptureExtension.class)
class NotificacionTurnosNoOpTest {

    private final NotificacionTurnos noOp = new NotificacionTurnosNoOp();

    private static Turno turnoDeFixture(String token, String email, String telefono, String dni) {
        return new Turno(new Paciente("Ana", "Prueba", dni), new Prestacion("Consulta", 30),
                Instant.parse("2026-11-25T13:00:00Z"), Instant.parse("2026-11-25T13:30:00Z"),
                EstadoTurno.PENDIENTE, token, email, telefono);
    }

    @Test
    void ofreceExactamenteUnAvisoPorCadaMailDeKb05() {
        List<String> metodos = Arrays.stream(NotificacionTurnos.class.getDeclaredMethods())
                .map(Method::getName).sorted().toList();

        assertThat(metodos).containsExactly("avisoTurnoConfirmado", "cancelacionPorBloqueo",
                "cancelacionPorOdontologo", "confirmacionReserva", "recordatorio", "reprogramacion",
                "reprogramacionMenos24h", "turnoNoConfirmado");
    }

    @Test
    void losOchoAvisosDevuelvenTrueSinLanzarNada() {
        Turno turno = turnoDeFixture("tok-secreto-123", "paciente@ejemplo.com", "11 5555-5555", "99999999");

        List<Predicate<Turno>> avisos = List.of(noOp::confirmacionReserva, noOp::avisoTurnoConfirmado,
                noOp::recordatorio, noOp::reprogramacion, noOp::reprogramacionMenos24h,
                noOp::cancelacionPorOdontologo, noOp::cancelacionPorBloqueo, noOp::turnoNoConfirmado);

        assertThat(avisos).hasSize(8).allSatisfy(aviso -> assertThat(aviso.test(turno)).isTrue());
    }

    @Test
    void noRegistraDatosPersonalesNiElToken(CapturedOutput salida) {
        Turno turno = turnoDeFixture("tok-secreto-123", "paciente@ejemplo.com", "11 5555-5555", "99999999");

        noOp.confirmacionReserva(turno);
        noOp.recordatorio(turno);
        noOp.turnoNoConfirmado(turno);

        assertThat(salida.getAll()).doesNotContain("paciente@ejemplo.com", "5555-5555", "99999999",
                "tok-secreto-123");
    }

    @Test
    void tampocoRegistraLosDatosDeOtroTurno(CapturedOutput salida) {
        Turno otro = turnoDeFixture("otro-token-xyz", "otra@ejemplo.com", "11 4444-4444", "88888888");

        noOp.cancelacionPorBloqueo(otro);
        noOp.reprogramacionMenos24h(otro);

        assertThat(salida.getAll()).doesNotContain("otra@ejemplo.com", "4444-4444", "88888888", "otro-token-xyz");
    }
}
