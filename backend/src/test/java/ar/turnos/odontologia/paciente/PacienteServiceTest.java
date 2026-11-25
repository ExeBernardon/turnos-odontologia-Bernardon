package ar.turnos.odontologia.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import ar.turnos.odontologia.common.ApiException;

class PacienteServiceTest {

    private PacienteRepository repo;
    private PacienteService service;

    @BeforeEach
    void preparar() {
        repo = mock(PacienteRepository.class);
        lenient().when(repo.contactosDelUltimoTurno(anyCollection())).thenReturn(List.of());
        service = new PacienteService(repo);
    }

    private static Paciente paciente(long id, String nombre, String apellido, String dni) {
        Paciente p = new Paciente(nombre, apellido, dni);
        org.springframework.test.util.ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static ContactoUltimoTurno contacto(long pacienteId, String email, String telefono) {
        return new ContactoFijo(pacienteId, email, telefono);
    }

    private record ContactoFijo(Long getPacienteId, String getEmail, String getTelefono)
            implements ContactoUltimoTurno {
    }

    // ---- decision DNI vs apellido (3.1) ----

    @Test
    void qNuloOEnBlancoListaATodos() {
        when(repo.findAllByOrderByApellidoAscNombreAscIdAsc()).thenReturn(List.of());

        service.buscar(null);
        service.buscar("   ");

        verify(repo, org.mockito.Mockito.times(2)).findAllByOrderByApellidoAscNombreAscIdAsc();
        verify(repo, never()).buscarPorPrefijoDni(any());
        verify(repo, never()).buscarPorPrefijoApellido(any());
    }

    @Test
    void qSoloDigitosBuscaPorPrefijoDeDniAunqueTengaPuntosOEspacios() {
        service.buscar("9999");
        service.buscar(" 99.999 ");

        verify(repo).buscarPorPrefijoDni("9999");
        verify(repo).buscarPorPrefijoDni("99999");
        verify(repo, never()).buscarPorPrefijoApellido(any());
    }

    @Test
    void qConLetrasBuscaPorPrefijoDeApellidoYRecortaBordes() {
        service.buscar(" ejem ");
        service.buscar("12ab");

        verify(repo).buscarPorPrefijoApellido("ejem");
        verify(repo).buscarPorPrefijoApellido("12ab");
        verify(repo, never()).buscarPorPrefijoDni(any());
    }

    @Test
    void qSoloDePuntosNoEsDniYSeBuscaComoApellido() {
        service.buscar("..");

        verify(repo).buscarPorPrefijoApellido("..");
        verify(repo, never()).buscarPorPrefijoDni(any());
    }

    // ---- armado de ultimoContacto (3.1) ----

    @Test
    void sinPacientesNoConsultaContactos() {
        when(repo.buscarPorPrefijoApellido("zzz")).thenReturn(List.of());

        assertThat(service.buscar("zzz")).isEmpty();

        verify(repo, never()).contactosDelUltimoTurno(anyCollection());
    }

    @Test
    void armaElContactoDeCadaPacienteSinMezclarYNullParaLosQueNoTienenTurnos() {
        Paciente ana = paciente(1L, "Ana", "Ejemplo", "99999999");
        Paciente carla = paciente(3L, "Carla", "Muestra", "99999997");
        Paciente beto = paciente(2L, "Beto", "Otro", "88888888");
        when(repo.findAllByOrderByApellidoAscNombreAscIdAsc()).thenReturn(List.of(ana, carla, beto));
        when(repo.contactosDelUltimoTurno(anyCollection())).thenReturn(List.of(
                contacto(1L, "ana@ejemplo.com", "111"), contacto(2L, "beto@ejemplo.com", "222")));

        List<PacienteResponse> r = service.buscar(null);

        assertThat(r).extracting(PacienteResponse::id).containsExactly(1L, 3L, 2L);
        assertThat(r.get(0).ultimoContacto()).isEqualTo(new ContactoResponse("ana@ejemplo.com", "111"));
        assertThat(r.get(1).ultimoContacto()).isNull();
        assertThat(r.get(2).ultimoContacto()).isEqualTo(new ContactoResponse("beto@ejemplo.com", "222"));
        verify(repo).contactosDelUltimoTurno(List.of(1L, 3L, 2L));
    }

    // ---- detalle ----

    @Test
    void obtenerDevuelveElPacienteConSuContacto() {
        when(repo.findById(1L)).thenReturn(Optional.of(paciente(1L, "Ana", "Ejemplo", "99999999")));
        when(repo.contactosDelUltimoTurno(anyCollection())).thenReturn(List.of(contacto(1L, "ana@ejemplo.com", "111")));

        PacienteResponse r = service.obtener(1L);

        assertThat(r.dni()).isEqualTo("99999999");
        assertThat(r.ultimoContacto()).isEqualTo(new ContactoResponse("ana@ejemplo.com", "111"));
    }

    @Test
    void obtenerInexistenteLanza404() {
        when(repo.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(9L))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getCodigo()).isEqualTo("RECURSO_NO_ENCONTRADO");
                });
    }

    // ---- alta y edicion: DNI duplicado (4.x) ----

    private static void assertDniDuplicado(Throwable e) {
        assertThat(e).isInstanceOfSatisfying(ApiException.class, a -> {
            assertThat(a.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(a.getCodigo()).isEqualTo("DNI_DUPLICADO");
            assertThat(a.getMessage()).isEqualTo("Ya existe un paciente con ese DNI.");
        });
    }

    @Test
    void crearNormalizaElDniYRecortaNombreYApellido() {
        when(repo.existsByDni("99999997")).thenReturn(false);
        when(repo.saveAndFlush(any(Paciente.class))).thenAnswer(i -> i.getArgument(0));

        PacienteResponse r = service.crear(new PacienteRequest(" Carla ", " Muestra ", "99.999.997"));

        assertThat(r.nombre()).isEqualTo("Carla");
        assertThat(r.apellido()).isEqualTo("Muestra");
        assertThat(r.dni()).isEqualTo("99999997");
        assertThat(r.ultimoContacto()).isNull();
    }

    @Test
    void crearConDniExistenteLanza422SinGuardar() {
        when(repo.existsByDni("99999997")).thenReturn(true);

        assertThatThrownBy(() -> service.crear(new PacienteRequest("Carla", "Muestra", "99.999.997")))
                .satisfies(PacienteServiceTest::assertDniDuplicado);

        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void crearTraduceLaViolacionDeLaUniqueDeDni() {
        when(repo.existsByDni("99999997")).thenReturn(false);
        when(repo.saveAndFlush(any(Paciente.class))).thenThrow(new DataIntegrityViolationException("x",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"paciente_dni_uk\"")));

        assertThatThrownBy(() -> service.crear(new PacienteRequest("Carla", "Muestra", "99999997")))
                .satisfies(PacienteServiceTest::assertDniDuplicado);
    }

    @Test
    void crearRelanzaCualquierOtraViolacionDeIntegridad() {
        DataIntegrityViolationException otra = new DataIntegrityViolationException("otra",
                new RuntimeException("violates not-null constraint \"paciente_nombre_nn\""));
        when(repo.existsByDni("99999997")).thenReturn(false);
        when(repo.saveAndFlush(any(Paciente.class))).thenThrow(otra);

        assertThatThrownBy(() -> service.crear(new PacienteRequest("Carla", "Muestra", "99999997")))
                .isSameAs(otra);
    }

    @Test
    void reemplazarConDniDeOtroPacienteLanza422SinModificar() {
        Paciente ana = paciente(1L, "Ana", "Ejemplo", "99999999");
        when(repo.findById(1L)).thenReturn(Optional.of(ana));
        when(repo.existsByDniAndIdNot("99999997", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.reemplazar(1L, new PacienteRequest("Ana", "Nueva", "99.999.997")))
                .satisfies(PacienteServiceTest::assertDniDuplicado);

        assertThat(ana.getApellido()).isEqualTo("Ejemplo");
        assertThat(ana.getDni()).isEqualTo("99999999");
        verify(repo, never()).saveAndFlush(any());
    }

    @Test
    void reemplazarActualizaLosTresCamposYDevuelveElContacto() {
        Paciente ana = paciente(1L, "Ana", "Ejemplo", "99999999");
        when(repo.findById(1L)).thenReturn(Optional.of(ana));
        when(repo.existsByDniAndIdNot("99999999", 1L)).thenReturn(false);
        when(repo.saveAndFlush(ana)).thenReturn(ana);
        when(repo.contactosDelUltimoTurno(anyCollection())).thenReturn(List.of(contacto(1L, "ana@ejemplo.com", "111")));

        PacienteResponse r = service.reemplazar(1L, new PacienteRequest(" Anita ", "Nueva", "99.999.999"));

        assertThat(r.nombre()).isEqualTo("Anita");
        assertThat(r.apellido()).isEqualTo("Nueva");
        assertThat(r.dni()).isEqualTo("99999999");
        assertThat(r.ultimoContacto().email()).isEqualTo("ana@ejemplo.com");
    }

    @Test
    void reemplazarInexistenteLanza404() {
        when(repo.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reemplazar(9L, new PacienteRequest("Ana", "Nueva", "99999999")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCodigo()).isEqualTo("RECURSO_NO_ENCONTRADO"));
    }
}
