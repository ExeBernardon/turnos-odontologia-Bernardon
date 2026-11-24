package ar.turnos.odontologia.horario;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;

import org.junit.jupiter.api.Test;

class DiaSemanaConverterTest {

    private final DiaSemanaConverter converter = new DiaSemanaConverter();

    @Test
    void guardaElNumeroIsoDelDia() {
        assertThat(converter.convertToDatabaseColumn(DayOfWeek.MONDAY)).isEqualTo((short) 1);
        assertThat(converter.convertToDatabaseColumn(DayOfWeek.WEDNESDAY)).isEqualTo((short) 3);
        assertThat(converter.convertToDatabaseColumn(DayOfWeek.SUNDAY)).isEqualTo((short) 7);
    }

    @Test
    void leeElDiaDesdeElNumeroIso() {
        assertThat(converter.convertToEntityAttribute((short) 1)).isEqualTo(DayOfWeek.MONDAY);
        assertThat(converter.convertToEntityAttribute((short) 5)).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(converter.convertToEntityAttribute((short) 7)).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    void nuloVaYVuelveNulo() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
