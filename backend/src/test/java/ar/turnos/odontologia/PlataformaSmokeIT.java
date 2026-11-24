package ar.turnos.odontologia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import ar.turnos.odontologia.config.AppProperties;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class PlataformaSmokeIT {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppProperties app;

    @Test
    void healthReportaUpSinAutenticacion() {
        ResponseEntity<String> r = http.getForEntity("/actuator/health", String.class);

        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void extensionBtreeGistInstaladaPorSchemaExtra() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);

        assertThat(n).isEqualTo(1);
    }

    @Test
    void jvmEnUtcYZonaDeNegocioConfigurada() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
        assertThat(app.zonaHoraria()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
    }
}
