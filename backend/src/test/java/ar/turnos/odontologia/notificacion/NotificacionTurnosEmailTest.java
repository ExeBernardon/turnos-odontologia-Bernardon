package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;

import ar.turnos.odontologia.AppPropertiesFixture;
import ar.turnos.odontologia.auth.Odontologo;
import ar.turnos.odontologia.auth.OdontologoRepository;
import ar.turnos.odontologia.turno.Turno;

class NotificacionTurnosEmailTest {

    private static final String EMAIL_ODONTOLOGO = "odontologo@ejemplo.com";

    private EmailService emails;
    private OdontologoRepository odontologos;
    private NotificacionTurnosEmail notificaciones;
    private final Turno turno = MotorPlantillasPrueba.turno("Ana", MotorPlantillasPrueba.TOKEN);

    @BeforeEach
    void preparar() {
        emails = mock(EmailService.class);
        odontologos = mock(OdontologoRepository.class);
        when(emails.enviar(any(), any(), any(), any())).thenReturn(true);
        notificaciones = new NotificacionTurnosEmail(emails, odontologos, AppPropertiesFixture.crear());
    }

    private void hayOdontologo(String email) {
        when(odontologos.findAll(any(Sort.class))).thenReturn(List.of(new Odontologo("Dr. Ejemplo", email, "hash")));
    }

    private boolean invocar(TipoMail tipo) {
        return switch (tipo) {
            case CONFIRMACION_RESERVA -> notificaciones.confirmacionReserva(turno);
            case AVISO_TURNO_CONFIRMADO -> notificaciones.avisoTurnoConfirmado(turno);
            case RECORDATORIO -> notificaciones.recordatorio(turno);
            case REPROGRAMACION -> notificaciones.reprogramacion(turno);
            case REPROGRAMACION_MENOS_24H -> notificaciones.reprogramacionMenos24h(turno);
            case CANCELACION_ODONTOLOGO -> notificaciones.cancelacionPorOdontologo(turno);
            case CANCELACION_BLOQUEO -> notificaciones.cancelacionPorBloqueo(turno);
            case TURNO_NO_CONFIRMADO -> notificaciones.turnoNoConfirmado(turno);
        };
    }

    @ParameterizedTest
    @EnumSource(value = TipoMail.class, mode = EnumSource.Mode.EXCLUDE, names = "TURNO_NO_CONFIRMADO")
    void losSieteMailsAlPacienteUsanSuTipoYVanAlEmailDeContactoDelTurno(TipoMail tipo) {
        assertThat(invocar(tipo)).isTrue();

        verify(emails).enviar(eq(tipo), any(), eq(MotorPlantillasPrueba.EMAIL_PACIENTE), any());
        verifyNoInteractions(odontologos);
    }

    @Test
    void elDestinoEsElEmailDeContactoDeEseTurnoYNoOtro() {
        turno.setEmailContacto("paciente.turno@ejemplo.com");

        notificaciones.recordatorio(turno);

        verify(emails).enviar(eq(TipoMail.RECORDATORIO), any(), eq("paciente.turno@ejemplo.com"), any());
        verify(emails, never()).enviar(any(), any(), eq(MotorPlantillasPrueba.EMAIL_PACIENTE), any());
    }

    @Test
    void elModeloLlevaLosLinksDelTipoYElContactoDelConsultorio() {
        notificaciones.recordatorio(turno);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> modelo = ArgumentCaptor.forClass(Map.class);
        verify(emails).enviar(eq(TipoMail.RECORDATORIO), any(), any(), modelo.capture());
        assertThat(modelo.getValue())
                .containsEntry("linkConfirmar", "http://localhost:8080/turno/confirmar?token=" + MotorPlantillasPrueba.TOKEN)
                .containsEntry("linkCancelar", "http://localhost:8080/turno/cancelar?token=" + MotorPlantillasPrueba.TOKEN)
                .containsEntry("contacto", AppPropertiesFixture.CONTACTO)
                .containsEntry("fechaHora", "jueves 10 de diciembre de 2026, 10:00");
    }

    @Test
    void elTurnoNoConfirmadoVaAlEmailDelOdontologoYNoAlDelTurno() {
        hayOdontologo(EMAIL_ODONTOLOGO);

        assertThat(notificaciones.turnoNoConfirmado(turno)).isTrue();

        verify(emails).enviar(eq(TipoMail.TURNO_NO_CONFIRMADO), any(), eq(EMAIL_ODONTOLOGO), any());
        verify(emails, never()).enviar(any(), any(), eq(MotorPlantillasPrueba.EMAIL_PACIENTE), any());
    }

    @Test
    void elTurnoNoConfirmadoUsaElEmailVigenteDelOdontologo() {
        hayOdontologo("otro.odontologo@ejemplo.com");

        notificaciones.turnoNoConfirmado(turno);

        verify(emails).enviar(eq(TipoMail.TURNO_NO_CONFIRMADO), any(), eq("otro.odontologo@ejemplo.com"), any());
    }

    @Test
    void sinOdontologoDevuelveFalseSinEnviarNiLanzar() {
        when(odontologos.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(notificaciones.turnoNoConfirmado(turno)).isFalse();

        verifyNoInteractions(emails);
    }

    @Test
    void elResultadoDelEnvioSePropagaALosCallers() {
        when(emails.enviar(any(), any(), any(), any())).thenReturn(false);

        assertThat(notificaciones.recordatorio(turno)).isFalse();
    }
}
