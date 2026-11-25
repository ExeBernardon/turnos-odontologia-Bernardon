package ar.turnos.odontologia.disponibilidad;

import static ar.turnos.odontologia.disponibilidad.ModoDisponibilidad.ODONTOLOGO;
import static ar.turnos.odontologia.disponibilidad.ModoDisponibilidad.PUBLICA;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.turnos.odontologia.horario.HorarioAtencion;

/** Tests unitarios del núcleo puro: sin Spring, sin base de datos y con reloj fijo. */
class CalculoDisponibilidadTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int PASO = 15;
    private static final int ANTICIPACION = 2;
    private static final int VENTANA = 60;

    /** Lunes 30/11/2026. */
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 30);
    /** 09:00 local (UTC-3). */
    private static final Instant AHORA = Instant.parse("2026-11-30T12:00:00Z");
    private static final Instant MUY_LEJOS = Instant.parse("2020-01-01T00:00:00Z");

    private static HorarioAtencion horario(LocalDate dia, String ini, String fin) {
        return new HorarioAtencion(dia.getDayOfWeek(), LocalTime.parse(ini), LocalTime.parse(fin));
    }

    private static Instant instant(LocalDate dia, String horaLocal) {
        return dia.atTime(LocalTime.parse(horaLocal)).atZone(ZONA).toInstant();
    }

    private static Intervalo iv(LocalDate dia, String ini, String fin) {
        return new Intervalo(instant(dia, ini), instant(dia, fin));
    }

    private static CalculoDisponibilidad calculo() {
        return new CalculoDisponibilidad(ZONA, PASO, ANTICIPACION, VENTANA);
    }

    // ---- 2.1: cálculo de la grilla ----

    @Test
    void diaSinHorariosDeAtencionDevuelveListaVacia() {
        assertThat(calculo().horariosLibres(LUNES, 30, List.of(), List.of(), ODONTOLOGO, MUY_LEJOS)).isEmpty();
    }

    @Test
    void rangoSimpleGeneraCandidatosCada15MinutosAlineadosAlInicio() {
        List<Instant> libres = calculo().horariosLibres(LUNES, 30,
                List.of(horario(LUNES, "09:00", "10:00")), List.of(), ODONTOLOGO, MUY_LEJOS);

        assertThat(libres).containsExactly(
                instant(LUNES, "09:00"), instant(LUNES, "09:15"), instant(LUNES, "09:30"));
    }

    @Test
    void bloqueoParcialQuitaSoloLosCandidatosQueSeSuperponen() {
        List<Instant> libres = calculo().horariosLibres(LUNES, 30,
                List.of(horario(LUNES, "09:00", "13:00")),
                List.of(iv(LUNES, "10:00", "11:00")), ODONTOLOGO, MUY_LEJOS);

        assertThat(libres).contains(instant(LUNES, "09:30"), instant(LUNES, "11:00"));
        assertThat(libres).doesNotContain(instant(LUNES, "09:45"), instant(LUNES, "10:00"),
                instant(LUNES, "10:45"), instant(LUNES, "12:45"));
    }

    @Test
    void turnoActivoQueParteElRangoDejaLibresLosTramosAnteriorYPosterior() {
        List<Instant> libres = calculo().horariosLibres(LUNES, 30,
                List.of(horario(LUNES, "09:00", "13:00")),
                List.of(iv(LUNES, "10:00", "10:30")), ODONTOLOGO, MUY_LEJOS);

        assertThat(libres).contains(instant(LUNES, "09:30"), instant(LUNES, "10:30"));
        assertThat(libres).doesNotContain(instant(LUNES, "10:00"), instant(LUNES, "10:15"));
    }

    @Test
    void duracionQueNoCabeEnElRangoNoAportaHorarios() {
        assertThat(calculo().horariosLibres(LUNES, 60,
                List.of(horario(LUNES, "09:00", "09:45")), List.of(), ODONTOLOGO, MUY_LEJOS)).isEmpty();
    }

    @Test
    void laGrillaSeAlineaAlInicioDelRangoOriginalNoAlDelSubrango() {
        List<Instant> libres = calculo().horariosLibres(LUNES, 15,
                List.of(horario(LUNES, "09:07", "10:07")),
                List.of(iv(LUNES, "09:37", "09:52")), ODONTOLOGO, MUY_LEJOS);

        assertThat(libres).containsExactly(
                instant(LUNES, "09:07"), instant(LUNES, "09:22"), instant(LUNES, "09:52"));
    }

    @Test
    void variosRangosElMismoDiaNoGeneranCandidatosEntreEllos() {
        List<Instant> libres = calculo().horariosLibres(LUNES, 45,
                List.of(horario(LUNES, "09:00", "13:00"), horario(LUNES, "15:00", "19:00")),
                List.of(), ODONTOLOGO, MUY_LEJOS);

        assertThat(libres).contains(instant(LUNES, "09:00"), instant(LUNES, "12:15"),
                instant(LUNES, "15:00"), instant(LUNES, "18:15"));
        assertThat(libres).doesNotContain(instant(LUNES, "13:00"), instant(LUNES, "14:00"));
    }

    // ---- 2.2: filtro temporal con reloj fijo ----

    @Test
    void unCandidatoEnElMomentoActualSeDescarta() {
        List<Instant> libres = calculo().horariosLibres(LUNES, 30,
                List.of(horario(LUNES, "09:00", "10:00")), List.of(), ODONTOLOGO, AHORA);

        assertThat(libres).doesNotContain(instant(LUNES, "09:00"));
        assertThat(libres).contains(instant(LUNES, "09:15"));
    }

    @Test
    void bordeInferiorDeLaVentanaPublicaEsInclusivo() {
        List<Instant> libres = calculo().horariosLibres(LUNES, 30,
                List.of(horario(LUNES, "09:00", "13:00")), List.of(), PUBLICA, AHORA);

        assertThat(libres).contains(instant(LUNES, "11:00"));
        assertThat(libres).doesNotContain(instant(LUNES, "10:45"));
    }

    @Test
    void bordeSuperiorDeLaVentanaPublicaEsInclusivo() {
        LocalDate limite = LUNES.plusDays(VENTANA);

        List<Instant> libres = calculo().horariosLibres(limite, 30,
                List.of(horario(limite, "09:00", "10:00")), List.of(), PUBLICA, AHORA);
        List<Instant> demasiadoLejos = calculo().horariosLibres(limite.plusDays(1), 30,
                List.of(horario(limite.plusDays(1), "09:00", "10:00")), List.of(), PUBLICA, AHORA);

        assertThat(libres).contains(instant(limite, "09:00"));
        assertThat(demasiadoLejos).isEmpty();
    }

    @Test
    void elModoOdontologoNoAplicaLaVentanaDeDosHoras() {
        List<Instant> libres = calculo().horariosLibres(LUNES, 30,
                List.of(horario(LUNES, "09:00", "13:00")), List.of(), ODONTOLOGO, AHORA);

        assertThat(libres).contains(instant(LUNES, "09:15"), instant(LUNES, "09:30"));
    }

    // ---- 2.3: validación reutilizable ----

    @Test
    void unInicioDentroDeLaGrillaYLibreEsValido() {
        ResultadoValidacion r = calculo().esValido(instant(LUNES, "09:00"), 30,
                List.of(horario(LUNES, "09:00", "13:00")), List.of(), ODONTOLOGO, MUY_LEJOS);

        assertThat(r.valido()).isTrue();
        assertThat(r.motivo()).isNull();
    }

    @Test
    void unInicioFueraDeLaGrillaSeRechazaConSuCausa() {
        ResultadoValidacion r = calculo().esValido(instant(LUNES, "09:07"), 30,
                List.of(horario(LUNES, "09:00", "13:00")), List.of(), ODONTOLOGO, MUY_LEJOS);

        assertThat(r.valido()).isFalse();
        assertThat(r.motivo()).contains("grilla");
    }

    @Test
    void unTurnoQueNoCabeEnElRangoSeRechazaConSuCausa() {
        ResultadoValidacion r = calculo().esValido(instant(LUNES, "09:00"), 60,
                List.of(horario(LUNES, "09:00", "09:45")), List.of(), ODONTOLOGO, MUY_LEJOS);

        assertThat(r.valido()).isFalse();
        assertThat(r.motivo()).contains("no entra");
    }

    @Test
    void unInicioSobreUnBloqueoOSobreUnTurnoActivoSeRechaza() {
        List<HorarioAtencion> horarios = List.of(horario(LUNES, "09:00", "13:00"));

        ResultadoValidacion bloqueo = calculo().esValido(instant(LUNES, "10:00"), 30, horarios,
                List.of(iv(LUNES, "10:00", "11:00")), ODONTOLOGO, MUY_LEJOS);
        ResultadoValidacion turno = calculo().esValido(instant(LUNES, "10:15"), 30, horarios,
                List.of(iv(LUNES, "10:00", "10:30")), ODONTOLOGO, MUY_LEJOS);

        assertThat(bloqueo.valido()).isFalse();
        assertThat(bloqueo.motivo()).contains("no está disponible");
        assertThat(turno.valido()).isFalse();
        assertThat(turno.motivo()).contains("no está disponible");
    }

    @Test
    void unInicioFueraDelHorarioDeAtencionSeRechaza() {
        ResultadoValidacion r = calculo().esValido(instant(LUNES, "14:00"), 30,
                List.of(horario(LUNES, "09:00", "13:00")), List.of(), ODONTOLOGO, MUY_LEJOS);

        assertThat(r.valido()).isFalse();
        assertThat(r.motivo()).contains("horario de atención");
    }

    @Test
    void unInicioEnElPasadoSeRechazaYUnoEnLaVentanaSeAcepta() {
        List<HorarioAtencion> horarios = List.of(horario(LUNES, "09:00", "13:00"));

        ResultadoValidacion pasado = calculo().esValido(instant(LUNES, "09:00"), 30, horarios,
                List.of(), ODONTOLOGO, AHORA);
        ResultadoValidacion dentro = calculo().esValido(instant(LUNES, "10:00"), 30, horarios,
                List.of(), ODONTOLOGO, AHORA);
        ResultadoValidacion fuera = calculo().esValido(instant(LUNES, "10:00"), 30, horarios,
                List.of(), PUBLICA, AHORA);

        assertThat(pasado.valido()).isFalse();
        assertThat(pasado.motivo()).contains("pasó");
        assertThat(dentro.valido()).isTrue();
        assertThat(fuera.valido()).isFalse();
        assertThat(fuera.motivo()).contains("ventana");
    }
}
