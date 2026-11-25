package ar.turnos.odontologia.disponibilidad;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import ar.turnos.odontologia.horario.HorarioAtencion;

/**
 * Núcleo del cálculo de disponibilidad (RN-DIS-01..06). Clase pura: no consulta la base, no usa
 * anotaciones Spring y recibe el momento actual por parámetro para poder testearla con reloj fijo.
 * Todos los rangos son semiabiertos {@code [inicio, fin)}.
 */
public class CalculoDisponibilidad {

    static final String MSG_PASADO = "Ese horario ya pasó. Elegí otro.";
    static final String MSG_VENTANA = "Ese horario está fuera de la ventana de reserva (entre 2 h y 60 días). Elegí otro.";
    static final String MSG_FUERA_ATENCION = "Ese horario está fuera del horario de atención.";
    static final String MSG_OCUPADO = "Ese horario no está disponible. Elegí otro.";

    private final ZoneId zona;
    private final int pasoMinutos;
    private final int anticipacionMinHoras;
    private final int ventanaMaxDias;

    public CalculoDisponibilidad(ZoneId zona, int pasoMinutos, int anticipacionMinHoras, int ventanaMaxDias) {
        if (zona == null || pasoMinutos < 1 || anticipacionMinHoras < 0 || ventanaMaxDias < 1) {
            throw new IllegalArgumentException("Parámetros de disponibilidad inválidos");
        }
        this.zona = zona;
        this.pasoMinutos = pasoMinutos;
        this.anticipacionMinHoras = anticipacionMinHoras;
        this.ventanaMaxDias = ventanaMaxDias;
    }

    /**
     * Horarios libres de un día para una prestación: atención menos ocupantes, discretizada con el
     * paso configurable alineado al inicio de cada rango original, ya filtrada por pasado/ventana.
     */
    public List<Instant> horariosLibres(LocalDate dia, int duracionMinutos,
            List<HorarioAtencion> horariosDelDia, Collection<Intervalo> ocupados,
            ModoDisponibilidad modo, Instant ahora) {
        if (dia == null || duracionMinutos < 1 || horariosDelDia == null || modo == null || ahora == null) {
            return List.of();
        }
        List<Instant> candidatos = new ArrayList<>();
        for (HorarioAtencion horario : horariosDelDia) {
            if (horario.getDiaSemana() != dia.getDayOfWeek()) {
                continue;
            }
            Intervalo rango = aIntervalo(horario, dia);
            for (Intervalo libre : rango.restar(ocupados)) {
                generarCandidatos(rango.inicio(), libre, duracionMinutos, candidatos);
            }
        }
        return candidatos.stream()
                .filter(inicio -> cumpleVentana(inicio, modo, ahora))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Verifica que un turno completo quepa en un rango de atención libre y cumpla la ventana. El
     * motivo del rechazo es apto para mostrar al usuario como mensaje del 422.
     */
    public ResultadoValidacion esValido(Instant inicio, int duracionMinutos,
            List<HorarioAtencion> horariosDelDia, Collection<Intervalo> ocupados,
            ModoDisponibilidad modo, Instant ahora) {
        if (inicio == null || duracionMinutos < 1 || horariosDelDia == null || modo == null || ahora == null) {
            return ResultadoValidacion.invalido(MSG_OCUPADO);
        }
        if (!inicio.isAfter(ahora)) {
            return ResultadoValidacion.invalido(MSG_PASADO);
        }
        if (modo == ModoDisponibilidad.PUBLICA && !cumpleVentana(inicio, modo, ahora)) {
            return ResultadoValidacion.invalido(MSG_VENTANA);
        }

        Instant fin = inicio.plusSeconds(duracionMinutos * 60L);
        Intervalo turno = new Intervalo(inicio, fin);
        LocalDate dia = LocalDate.ofInstant(inicio, zona);
        for (HorarioAtencion horario : horariosDelDia) {
            if (horario.getDiaSemana() != dia.getDayOfWeek()) {
                continue;
            }
            Intervalo rango = aIntervalo(horario, dia);
            if (!rango.interseca(turno)) {
                continue;
            }
            if (!estaEnGrilla(inicio, rango.inicio())) {
                return ResultadoValidacion.invalido(
                        "Ese horario no respeta la grilla de " + pasoMinutos + " minutos.");
            }
            if (fin.isAfter(rango.fin())) {
                return ResultadoValidacion.invalido(
                        "La duración de la prestación no entra en el rango de atención.");
            }
            for (Intervalo libre : rango.restar(ocupados)) {
                if (libre.contiene(turno)) {
                    return ResultadoValidacion.aceptado();
                }
            }
            return ResultadoValidacion.invalido(MSG_OCUPADO);
        }
        return ResultadoValidacion.invalido(MSG_FUERA_ATENCION);
    }

    private void generarCandidatos(Instant origenGrilla, Intervalo libre, int duracionMinutos,
            List<Instant> destino) {
        long pasoSegundos = pasoMinutos * 60L;
        long duracionSegundos = duracionMinutos * 60L;
        for (Instant candidato = origenGrilla; candidato.isBefore(libre.fin());
                candidato = candidato.plusSeconds(pasoSegundos)) {
            if (!candidato.isBefore(libre.inicio())
                    && !candidato.plusSeconds(duracionSegundos).isAfter(libre.fin())) {
                destino.add(candidato);
            }
        }
    }

    private boolean cumpleVentana(Instant inicio, ModoDisponibilidad modo, Instant ahora) {
        if (!inicio.isAfter(ahora)) {
            return false;
        }
        if (modo == ModoDisponibilidad.PUBLICA) {
            return !inicio.isBefore(ahora.plus(anticipacionMinHoras, ChronoUnit.HOURS))
                    && !inicio.isAfter(ahora.plus(ventanaMaxDias, ChronoUnit.DAYS));
        }
        return true;
    }

    private boolean estaEnGrilla(Instant inicio, Instant origen) {
        if (inicio.isBefore(origen)) {
            return false;
        }
        long deltaSegundos = Duration.between(origen, inicio).getSeconds();
        return deltaSegundos % (pasoMinutos * 60L) == 0;
    }

    private Intervalo aIntervalo(HorarioAtencion horario, LocalDate dia) {
        return new Intervalo(
                horario.getHoraInicio().atDate(dia).atZone(zona).toInstant(),
                horario.getHoraFin().atDate(dia).atZone(zona).toInstant());
    }
}
