package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ar.turnos.odontologia.IntegracionPostgres;

@IntegracionPostgres
class NotificacionTurnosIT {

    @Autowired
    private NotificacionTurnos notificaciones;

    @Autowired
    private List<NotificacionTurnos> implementaciones;

    @Test
    void enLaAppSeInyectaLaImplementacionQueEnviaPorEmail() {
        assertThat(notificaciones).isInstanceOf(NotificacionTurnosEmail.class);
    }

    @Test
    void laImplementacionPorEmailEsLaUnicaRegistrada() {
        assertThat(implementaciones).hasSize(1).first().isInstanceOf(NotificacionTurnosEmail.class);
    }
}
