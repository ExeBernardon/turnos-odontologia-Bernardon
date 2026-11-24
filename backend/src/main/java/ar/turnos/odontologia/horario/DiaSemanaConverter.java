package ar.turnos.odontologia.horario;

import java.time.DayOfWeek;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Guarda el dia de la semana como numero ISO (1 = lunes a 7 = domingo) en una columna smallint. */
@Converter
public class DiaSemanaConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek dia) {
        return dia == null ? null : (short) dia.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short valor) {
        return valor == null ? null : DayOfWeek.of(valor);
    }
}
