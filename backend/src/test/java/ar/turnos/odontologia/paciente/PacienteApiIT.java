package ar.turnos.odontologia.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.turnos.odontologia.IntegracionPostgres;
import ar.turnos.odontologia.prestacion.Prestacion;
import ar.turnos.odontologia.prestacion.PrestacionRepository;
import ar.turnos.odontologia.turno.EstadoTurno;
import ar.turnos.odontologia.turno.Turno;
import ar.turnos.odontologia.turno.TurnoRepository;

@IntegracionPostgres
@AutoConfigureMockMvc
class PacienteApiIT {

    private static final Path EJEMPLOS = Path.of("..", "docs", "api", "ejemplos");
    private static final String PRESTACION = "C09 prueba";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PacienteRepository pacientes;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private TurnoRepository turnos;

    @AfterEach
    void limpiar() {
        jdbc.update("TRUNCATE turno, paciente RESTART IDENTITY CASCADE");
        jdbc.update("DELETE FROM prestacion WHERE nombre = ?", PRESTACION);
    }

    private static String body(String nombre, String apellido, String dni) {
        return "{\"nombre\":\"" + nombre + "\",\"apellido\":\"" + apellido + "\",\"dni\":\"" + dni + "\"}";
    }

    private Set<String> clavesDelEjemplo(String archivo) throws Exception {
        JsonNode n = json.readTree(Files.readString(EJEMPLOS.resolve(archivo)));
        return claves(n.isArray() ? n.get(0) : n);
    }

    private static Set<String> claves(JsonNode n) {
        Set<String> s = new TreeSet<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext();) {
            s.add(it.next());
        }
        return s;
    }

    private Paciente guardar(String nombre, String apellido, String dni) {
        return pacientes.saveAndFlush(new Paciente(nombre, apellido, dni));
    }

    private void turno(Paciente p, int hora, String email, String telefono, String creadoEn) {
        Prestacion prestacion = prestaciones.findAll().stream().filter(x -> x.getNombre().equals(PRESTACION))
                .findFirst().orElseGet(() -> prestaciones.saveAndFlush(new Prestacion(PRESTACION, 30)));
        OffsetDateTime inicio = OffsetDateTime.parse("2026-11-25T00:00:00-03:00").plusHours(hora);
        Turno t = turnos.saveAndFlush(new Turno(p, prestacion, inicio.toInstant(),
                inicio.plusMinutes(30).toInstant(), EstadoTurno.PENDIENTE, "c09-" + p.getId() + "-" + hora, email,
                telefono));
        jdbc.update("UPDATE turno SET creado_en = ?::timestamptz WHERE id = ?", creadoEn, t.getId());
    }

    private int cantidadConDni(String dni) {
        return jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = ?", Integer.class, dni);
    }

    // ---- seguridad ----

    @Test
    void sinTokenTodosLosEndpointsDan401() throws Exception {
        mvc.perform(get("/api/pacientes")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/api/pacientes/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON)
                .content(body("Carla", "Muestra", "99999997"))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/pacientes/1").contentType(MediaType.APPLICATION_JSON)
                .content(body("Carla", "Muestra", "99999997"))).andExpect(status().isUnauthorized());

        assertThat(pacientes.count()).isZero();
    }

    // ---- listado (3.2) ----

    @Test
    @WithMockUser
    void listadoDevuelveLasClavesDelContratoYElContactoDeCadaPaciente() throws Exception {
        Paciente ana = guardar("Ana", "Ejemplo", "99999999");
        guardar("Carla", "Muestra", "99999997");
        turno(ana, 9, "paciente@ejemplo.com", "+54 9 11 0000-0000", "2026-11-01T10:00:00Z");

        String respuesta = mvc.perform(get("/api/pacientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].apellido").value("Ejemplo"))
                .andExpect(jsonPath("$[0].ultimoContacto.email").value("paciente@ejemplo.com"))
                .andExpect(jsonPath("$[0].ultimoContacto.telefono").value("+54 9 11 0000-0000"))
                .andExpect(jsonPath("$[1].apellido").value("Muestra"))
                .andExpect(jsonPath("$[1].ultimoContacto").value((Object) null))
                .andReturn().getResponse().getContentAsString();

        JsonNode lista = json.readTree(respuesta);
        assertThat(claves(lista.get(0))).isEqualTo(clavesDelEjemplo("pacientes-listar.200.json"));
        assertThat(claves(lista.get(1))).isEqualTo(clavesDelEjemplo("pacientes-listar.200.json"));
    }

    @Test
    @WithMockUser
    void listadoBuscaPorDniConPuntosYPorApellido() throws Exception {
        guardar("Ana", "Ejemplo", "99999999");
        guardar("Beto", "Muestra", "88888888");

        mvc.perform(get("/api/pacientes").param("q", "99.999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dni").value("99999999"));
        mvc.perform(get("/api/pacientes").param("q", "mues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dni").value("88888888"));
    }

    @Test
    @WithMockUser
    void listadoSinCoincidenciasDa200ConListaVacia() throws Exception {
        guardar("Ana", "Ejemplo", "99999999");

        mvc.perform(get("/api/pacientes").param("q", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/pacientes").param("q", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- detalle (3.3) ----

    @Test
    @WithMockUser
    void detalleDevuelveElContactoDelUltimoTurnoCreado() throws Exception {
        Paciente ana = guardar("Ana", "Ejemplo", "99999999");
        turno(ana, 15, "viejo@ejemplo.com", "111", "2026-11-01T10:00:00Z");
        turno(ana, 9, "nuevo@ejemplo.com", "222", "2026-11-02T10:00:00Z");

        String respuesta = mvc.perform(get("/api/pacientes/" + ana.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.getId()))
                .andExpect(jsonPath("$.dni").value("99999999"))
                .andExpect(jsonPath("$.ultimoContacto.email").value("nuevo@ejemplo.com"))
                .andExpect(jsonPath("$.ultimoContacto.telefono").value("222"))
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(respuesta))).isEqualTo(clavesDelEjemplo("pacientes-obtener.200.json"));
    }

    @Test
    @WithMockUser
    void detalleSinTurnosTraeUltimoContactoNullConLaClavePresente() throws Exception {
        Paciente carla = guardar("Carla", "Muestra", "99999997");

        String respuesta = mvc.perform(get("/api/pacientes/" + carla.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoContacto").value((Object) null))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(respuesta).has("ultimoContacto")).isTrue();
    }

    @Test
    @WithMockUser
    void detalleInexistenteDa404YIdNoNumericoDa400() throws Exception {
        mvc.perform(get("/api/pacientes/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
        mvc.perform(get("/api/pacientes/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_MAL_FORMADA"));
    }

    // ---- alta (4.2) ----

    @Test
    @WithMockUser
    void altaConElRequestDeEjemploDevuelve201ConDniNormalizado() throws Exception {
        String request = Files.readString(EJEMPLOS.resolve("pacientes-crear.request.json"));

        String respuesta = mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Carla"))
                .andExpect(jsonPath("$.apellido").value("Muestra"))
                .andExpect(jsonPath("$.dni").value("99999997"))
                .andExpect(jsonPath("$.ultimoContacto").value((Object) null))
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(respuesta))).isEqualTo(clavesDelEjemplo("pacientes-crear.201.json"));
        assertThat(cantidadConDni("99999997")).isEqualTo(1);
    }

    @Test
    @WithMockUser
    void altaDeSieteDigitosSeAceptaTalCual() throws Exception {
        mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON)
                .content(body("Dora", "Prueba", "9999999")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dni").value("9999999"));
    }

    @Test
    @WithMockUser
    void altaInvalidaDa400ConDetalleDelCampo() throws Exception {
        mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON)
                .content(body("Carla", "Muestra", "99-999-997")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("dni"));
        mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON)
                .content(body("   ", "Muestra", "99999997")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("nombre"));

        assertThat(pacientes.count()).isZero();
    }

    @Test
    @WithMockUser
    void altaConJsonIlegibleDa400SolicitudMalFormada() throws Exception {
        mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_MAL_FORMADA"));

        assertThat(pacientes.count()).isZero();
    }

    @Test
    @WithMockUser
    void altaIgnoraEmailYTelefonoDelBody() throws Exception {
        String request = "{\"nombre\":\"Carla\",\"apellido\":\"Muestra\",\"dni\":\"99999997\","
                + "\"email\":\"x@ejemplo.com\",\"telefono\":\"111\"}";

        String respuesta = mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ultimoContacto").value((Object) null))
                .andReturn().getResponse().getContentAsString();

        JsonNode n = json.readTree(respuesta);
        assertThat(n.has("email")).isFalse();
        assertThat(n.has("telefono")).isFalse();
    }

    @Test
    @WithMockUser
    void altaConDniExistenteEscritoConPuntosDa422() throws Exception {
        guardar("Ana", "Ejemplo", "99999997");

        mvc.perform(post("/api/pacientes").contentType(MediaType.APPLICATION_JSON)
                .content(Files.readString(EJEMPLOS.resolve("pacientes-crear.request.json"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DNI_DUPLICADO"))
                .andExpect(jsonPath("$.mensaje").value("Ya existe un paciente con ese DNI."))
                .andExpect(jsonPath("$.path").value("/api/pacientes"));

        assertThat(cantidadConDni("99999997")).isEqualTo(1);
    }

    // ---- carrera de altas (4.3) ----

    @Test
    void dosAltasSimultaneasConElMismoDniDan201Y422NuncaUn500() throws Exception {
        int hilos = 2;
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                String nombre = "Carrera" + i;
                resultados.add(pool.submit(() -> {
                    listos.countDown();
                    largada.await();
                    return mvc.perform(post("/api/pacientes").with(user("odontologo"))
                            .contentType(MediaType.APPLICATION_JSON).content(body(nombre, "Muestra", "99999997")))
                            .andReturn().getResponse().getStatus();
                }));
            }
            listos.await();
            largada.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> f : resultados) {
                estados.add(f.get());
            }

            assertThat(estados).containsExactlyInAnyOrder(201, 422);
            assertThat(cantidadConDni("99999997")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- edicion (4.4) ----

    @Test
    @WithMockUser
    void edicionConElRequestDeEjemploDevuelve200() throws Exception {
        Paciente p = guardar("Carla", "Vieja", "99999997");

        String respuesta = mvc.perform(put("/api/pacientes/" + p.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(Files.readString(EJEMPLOS.resolve("pacientes-actualizar.request.json"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p.getId()))
                .andExpect(jsonPath("$.apellido").value("Muestra Prueba"))
                .andExpect(jsonPath("$.dni").value("99999997"))
                .andReturn().getResponse().getContentAsString();

        assertThat(claves(json.readTree(respuesta))).isEqualTo(clavesDelEjemplo("pacientes-actualizar.200.json"));
    }

    @Test
    @WithMockUser
    void edicionConservandoElPropioDniNoDa422() throws Exception {
        Paciente p = guardar("Ana", "Ejemplo", "99999999");

        mvc.perform(put("/api/pacientes/" + p.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body("Ana", "Nuevo", "99.999.999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apellido").value("Nuevo"))
                .andExpect(jsonPath("$.dni").value("99999999"));
    }

    @Test
    @WithMockUser
    void edicionConDniDeOtroPacienteDa422YNoModificaNada() throws Exception {
        Paciente a = guardar("Ana", "Ejemplo", "99999999");
        guardar("Beto", "Muestra", "88888888");

        mvc.perform(put("/api/pacientes/" + a.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body("Ana", "Cambiada", "88.888.888")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("DNI_DUPLICADO"))
                .andExpect(jsonPath("$.path").value("/api/pacientes/" + a.getId()));

        Paciente leida = pacientes.findById(a.getId()).orElseThrow();
        assertThat(leida.getApellido()).isEqualTo("Ejemplo");
        assertThat(leida.getDni()).isEqualTo("99999999");
    }

    @Test
    @WithMockUser
    void edicionDeInexistenteDa404() throws Exception {
        mvc.perform(put("/api/pacientes/999999").contentType(MediaType.APPLICATION_JSON)
                .content(body("Ana", "Ejemplo", "99999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @WithMockUser
    void edicionInvalidaDa400() throws Exception {
        Paciente p = guardar("Ana", "Ejemplo", "99999999");

        mvc.perform(put("/api/pacientes/" + p.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body("Ana", "Ejemplo", "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0].campo").value("dni"));
    }

    @Test
    @WithMockUser
    void editarUnPacienteNoTocaElContactoDeSusTurnos() throws Exception {
        Paciente p = guardar("Ana", "Ejemplo", "99999999");
        turno(p, 9, "paciente@ejemplo.com", "111", "2026-11-01T10:00:00Z");

        mvc.perform(put("/api/pacientes/" + p.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body("Anita", "Nueva", "99999999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ultimoContacto.email").value("paciente@ejemplo.com"));

        assertThat(jdbc.queryForObject("SELECT email_contacto FROM turno", String.class))
                .isEqualTo("paciente@ejemplo.com");
        assertThat(jdbc.queryForObject("SELECT telefono_contacto FROM turno", String.class)).isEqualTo("111");
    }

    // ---- sin borrado (4.5) ----

    @Test
    @WithMockUser
    void deleteNoExisteDa404YElPacienteSigue() throws Exception {
        Paciente p = guardar("Ana", "Ejemplo", "99999999");

        mvc.perform(delete("/api/pacientes/" + p.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));

        assertThat(pacientes.existsById(p.getId())).isTrue();
    }
}
