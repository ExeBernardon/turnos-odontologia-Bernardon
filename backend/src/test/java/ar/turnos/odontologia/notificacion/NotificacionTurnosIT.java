package ar.turnos.odontologia.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.turno.Turno;

@IntegracionPostgres
class NotificacionTurnosIT {

    @Autowired
    private NotificacionTurnos notificaciones;

    /** Implementacion "real" de prueba: distinguible del no-op porque devuelve false. */
    static class ImplementacionReal implements NotificacionTurnos {
        public boolean confirmacionReserva(Turno t) { return false; }
        public boolean avisoTurnoConfirmado(Turno t) { return false; }
        public boolean recordatorio(Turno t) { return false; }
        public boolean reprogramacion(Turno t) { return false; }
        public boolean reprogramacionMenos24h(Turno t) { return false; }
        public boolean cancelacionPorOdontologo(Turno t) { return false; }
        public boolean cancelacionPorBloqueo(Turno t) { return false; }
        public boolean turnoNoConfirmado(Turno t) { return false; }
    }

    @Test
    void enLaAppSeInyectaLaImplementacionQueNoEnvia() {
        assertThat(notificaciones).isInstanceOf(NotificacionTurnosNoOp.class);
    }

    @Test
    void otraImplementacionReemplazaAlNoOpSinPrimary() {
        new ApplicationContextRunner()
                .withBean(NotificacionTurnosNoOp.class)
                .withBean("implementacionReal", ImplementacionReal.class)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(NotificacionTurnos.class)).isSameAs(ctx.getBean("implementacionReal"));
                });
    }

    @Test
    void sinOtraImplementacionElNoOpEsLaUnica() {
        new ApplicationContextRunner()
                .withBean(NotificacionTurnosNoOp.class)
                .run(ctx -> assertThat(ctx.getBean(NotificacionTurnos.class))
                        .isInstanceOf(NotificacionTurnosNoOp.class));
    }
}
