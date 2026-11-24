package ar.turnos.odontologia.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import ar.turnos.odontologia.IntegracionPostgres;

/** Reproduce lo que hace Spring en cada arranque: volver a correr schema-extra.sql con el separador ^^^. */
@IntegracionPostgres
class SchemaExtraIdempotenteIT {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    private void ejecutarScript() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(false, false, "UTF-8",
                new ClassPathResource("schema-extra.sql"));
        populator.setSeparator("^^^");
        populator.execute(dataSource);
    }

    private int contar(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void reejecutarElScriptNoFallaNiDuplicaLaRestriccion() {
        assertThatCode(this::ejecutarScript).doesNotThrowAnyException();

        assertThat(contar("SELECT count(*) FROM pg_constraint WHERE conname = 'turno_sin_solapamiento'"))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'")).isEqualTo(1);
    }

    @Test
    void reejecutarVariasVecesSigueIgual() {
        ejecutarScript();
        ejecutarScript();

        assertThat(contar("SELECT count(*) FROM pg_constraint WHERE conname = 'turno_sin_solapamiento' "
                + "AND contype = 'x' AND conrelid = 'turno'::regclass")).isEqualTo(1);
    }
}
