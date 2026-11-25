package ar.turnos.odontologia.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Ley 25.326: Hibernate registra el detalle de PostgreSQL ({@code Key (dni)=(...)}) al fallar una
 * restriccion de unicidad; ese logger se apaga para que el DNI no llegue a los logs.
 */
class LoggingSinDatosPersonalesTest {

    @Test
    void elLoggerDeErroresSqlDeHibernateEstaApagado() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties propiedades = yaml.getObject();

        assertThat(propiedades).isNotNull();
        assertThat(propiedades.getProperty("logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper"))
                .isEqualTo("OFF");
    }
}
