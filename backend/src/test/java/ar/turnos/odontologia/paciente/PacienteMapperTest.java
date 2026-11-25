package ar.turnos.odontologia.paciente;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class PacienteMapperTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void ultimoContactoNuloSeSerializaComoClaveNull() throws Exception {
        PacienteResponse r = PacienteMapper.aRespuesta(new Paciente("Carla", "Muestra", "99999997"), null);

        JsonNode n = json.readTree(json.writeValueAsString(r));

        assertThat(n.has("ultimoContacto")).isTrue();
        assertThat(n.get("ultimoContacto").isNull()).isTrue();
        assertThat(n.get("dni").asText()).isEqualTo("99999997");
        assertThat(n.size()).isEqualTo(5);
    }

    @Test
    void ultimoContactoPresenteSeSerializaConEmailYTelefono() throws Exception {
        PacienteResponse r = PacienteMapper.aRespuesta(new Paciente("Ana", "Ejemplo", "99999999"),
                new ContactoResponse("paciente@ejemplo.com", "+54 9 11 0000-0000"));

        JsonNode n = json.readTree(json.writeValueAsString(r));

        assertThat(n.get("ultimoContacto").get("email").asText()).isEqualTo("paciente@ejemplo.com");
        assertThat(n.get("ultimoContacto").get("telefono").asText()).isEqualTo("+54 9 11 0000-0000");
    }
}
