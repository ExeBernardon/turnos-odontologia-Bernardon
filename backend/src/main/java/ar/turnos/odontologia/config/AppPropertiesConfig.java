package ar.turnos.odontologia.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra {@link AppProperties}. Los {@code @WebMvcTest} la importan explicitamente. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties.class)
public class AppPropertiesConfig {
}
