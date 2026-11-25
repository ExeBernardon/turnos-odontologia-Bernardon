package ar.turnos.odontologia.disponibilidad;

import java.util.List;

/** Schema `Disponibilidad` del contrato: la prestación y un elemento por fecha del rango pedido. */
public record DisponibilidadResponse(PrestacionRefResponse prestacion, List<DiaDisponibleResponse> dias) {
}
