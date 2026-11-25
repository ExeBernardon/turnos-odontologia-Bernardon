package ar.turnos.odontologia.turno;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

/**
 * Acepta solo instantes ISO-8601 con offset explicito (schema {@code Instante} del contrato).
 * Un valor sin offset se devuelve como {@code null} para que {@code @NotNull} lo informe
 * como violacion de {@code inicio} y el handler responda 400 VALIDACION con detalles.
 */
public class InstanteConOffsetDeserializer extends JsonDeserializer<OffsetDateTime> {

    private static final String INSTANTE_CON_OFFSET =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})$";

    @Override
    public OffsetDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String valor = parser.getValueAsString();
        if (valor == null || !valor.matches(INSTANTE_CON_OFFSET)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(valor);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
