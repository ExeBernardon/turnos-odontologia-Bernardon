package ar.turnos.odontologia.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

/**
 * Verifica el mapeo explicito de las variables de entorno de KB 08 a las claves {@code app.*}
 * de {@code application.yml}. Las variables se simulan con system properties (las placeholders
 * del YAML resuelven igual desde el entorno real).
 */
class AppPropertiesTest {

    private static final String JWT = "JWT_SECRET=secreto-de-prueba-ficticio-de-al-menos-32-bytes";
    private static final String SEED = "SEED_ODONTOLOGO_PASSWORD=password-de-prueba-ficticia";

    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    private ApplicationContextRunner runner(String... sistema) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(Config.class)
                .withSystemProperties(sistema);
    }

    @Test
    void bindeaVariableConGuionDesdeElEntorno() {
        runner(JWT, SEED, "APP_CANCELACION_HORAS_MINIMAS=12", "APP_RECORDATORIO_INTERVALO_MIN=5")
                .run(ctx -> {
                    AppProperties app = ctx.getBean(AppProperties.class);
                    assertThat(app.cancelacion().horasMinimas()).isEqualTo(12);
                    assertThat(app.recordatorio().intervaloMin()).isEqualTo(5);
                });
    }

    @Test
    void aplicaDefaultsNoSensibles() {
        runner(JWT, SEED).run(ctx -> {
            AppProperties app = ctx.getBean(AppProperties.class);
            assertThat(app.agenda().pasoMinutos()).isEqualTo(15);
            assertThat(app.recordatorio().horasAntes()).isEqualTo(48);
            assertThat(app.alerta().pendienteHoras()).isEqualTo(24);
            assertThat(app.turno().anticipacionMinHoras()).isEqualTo(2);
            assertThat(app.turno().ventanaMaxDias()).isEqualTo(60);
            assertThat(app.jwt().expirationMinutes()).isEqualTo(480);
            assertThat(app.zonaHoraria()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
            assertThat(app.mail().from()).isEqualTo("turnos@consultorio.local");
        });
    }

    @Test
    void sinJwtSecretElArranqueFallaNombrandoLaPropiedad() {
        runner(SEED).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("app.jwt.secret");
        });
    }

    @Test
    void sinPasswordDelSeedElArranqueFallaNombrandoLaPropiedad() {
        runner(JWT).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).hasStackTraceContaining("app.seed.odontologo.password");
        });
    }
}
