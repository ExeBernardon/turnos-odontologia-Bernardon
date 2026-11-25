import { pedir } from '../../api/cliente';
import type { DiaDisponible, Disponibilidad, Prestacion, Turno, TurnoManualRequest } from '../../api/types';
import { ErrorRespuestaAgenda, parsearTurno } from '../agenda/agenda.api';
import { esObjeto, leerNumero, leerTexto } from '../configuracion/parseo';
import { listarPrestaciones } from '../configuracion/prestaciones.api';

function parsearTurnoRespuesta(dato: unknown): Turno {
  const turno = parsearTurno(dato);
  if (turno === null) {
    throw new ErrorRespuestaAgenda('turno');
  }
  return turno;
}

export function confirmarTurno(id: number): Promise<Turno> {
  return pedir('POST', `/api/turnos/${id}/confirmar`, { parsear: parsearTurnoRespuesta });
}

export function cancelarTurno(id: number): Promise<Turno> {
  return pedir('POST', `/api/turnos/${id}/cancelar`, { parsear: parsearTurnoRespuesta });
}

export function marcarAsistencia(id: number, estado: 'ASISTIO' | 'AUSENTE'): Promise<Turno> {
  return pedir('PATCH', `/api/turnos/${id}/estado`, { body: { estado }, parsear: parsearTurnoRespuesta });
}

export function reprogramarTurno(id: number, inicio: string): Promise<Turno> {
  return pedir('PATCH', `/api/turnos/${id}/reprogramar`, { body: { inicio }, parsear: parsearTurnoRespuesta });
}

export function crearTurnoManual(body: TurnoManualRequest): Promise<Turno> {
  return pedir('POST', '/api/turnos', { body, parsear: parsearTurnoRespuesta });
}

function parsearDia(dato: unknown): DiaDisponible {
  if (!esObjeto(dato) || !Array.isArray(dato['horarios'])) {
    throw new ErrorRespuestaAgenda('dias');
  }
  const horarios = dato['horarios'].map((h: unknown) => {
    if (typeof h !== 'string') {
      throw new ErrorRespuestaAgenda('horarios');
    }
    return h;
  });
  return { fecha: leerTexto(dato, 'fecha'), horarios };
}

function parsearDisponibilidad(dato: unknown): Disponibilidad {
  if (!esObjeto(dato) || !esObjeto(dato['prestacion']) || !Array.isArray(dato['dias'])) {
    throw new ErrorRespuestaAgenda('disponibilidad');
  }
  const prestacion = dato['prestacion'];
  return {
    prestacion: {
      id: leerNumero(prestacion, 'id'),
      nombre: leerTexto(prestacion, 'nombre'),
      duracionMinutos: leerNumero(prestacion, 'duracionMinutos'),
    },
    dias: dato['dias'].map(parsearDia),
  };
}

/** Horarios libres de un dia para el odontologo: endpoint privado, sin la ventana de 2 h / 60 dias del publico. */
export function obtenerDisponibilidadAdmin(prestacionId: number, fecha: string): Promise<Disponibilidad> {
  const consulta = `prestacionId=${prestacionId}&desde=${encodeURIComponent(fecha)}&hasta=${encodeURIComponent(fecha)}`;
  return pedir('GET', `/api/disponibilidad?${consulta}`, { parsear: parsearDisponibilidad });
}

export async function listarPrestacionesActivas(): Promise<Prestacion[]> {
  const prestaciones = await listarPrestaciones();
  return prestaciones.filter((p) => p.activa);
}
