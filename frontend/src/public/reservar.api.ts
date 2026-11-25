import { pedir } from '../api/cliente';
import type { Disponibilidad, DiaDisponible, EstadoTurno, PrestacionPublica, TurnoResumenPublico, ReservaPublicaRequest } from '../api/types';
import { esObjeto, leerBooleano, leerNumero, leerTexto, parsearLista } from '../admin/configuracion/parseo';

const ESTADOS: readonly EstadoTurno[] = ['PENDIENTE', 'CONFIRMADO', 'CANCELADO', 'ASISTIO', 'AUSENTE'];

function parsearPrestacionPublica(dato: unknown): PrestacionPublica {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: prestación');
  }
  return { id: leerNumero(dato, 'id'), nombre: leerTexto(dato, 'nombre'), duracionMinutos: leerNumero(dato, 'duracionMinutos') };
}

function parsearDia(dato: unknown): DiaDisponible {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: día');
  }
  return { fecha: leerTexto(dato, 'fecha'), horarios: parsearLista(dato['horarios'], (h) => {
    if (typeof h !== 'string') {
      throw new Error('Respuesta inválida: horarios');
    }
    return h;
  }) };
}

function parsearDisponibilidad(dato: unknown): Disponibilidad {
  if (!esObjeto(dato) || !esObjeto(dato['prestacion'])) {
    throw new Error('Respuesta inválida: disponibilidad');
  }
  return { prestacion: parsearPrestacionPublica(dato['prestacion']), dias: parsearLista(dato['dias'], parsearDia) };
}

function parsearEstado(dato: Record<string, unknown>): EstadoTurno {
  const estado = leerTexto(dato, 'estado');
  const conocido = ESTADOS.find((e) => e === estado);
  if (conocido === undefined) {
    throw new Error('Respuesta inválida: estado');
  }
  return conocido;
}

export function parsearResumen(dato: unknown): TurnoResumenPublico {
  if (!esObjeto(dato) || !esObjeto(dato['prestacion'])) {
    throw new Error('Respuesta inválida: turno');
  }
  return {
    prestacion: { nombre: leerTexto(dato['prestacion'], 'nombre'), duracionMinutos: leerNumero(dato['prestacion'], 'duracionMinutos') },
    inicio: leerTexto(dato, 'inicio'),
    fin: leerTexto(dato, 'fin'),
    estado: parsearEstado(dato),
    nombrePaciente: leerTexto(dato, 'nombrePaciente'),
    puedeConfirmar: leerBooleano(dato, 'puedeConfirmar'),
    puedeCancelar: leerBooleano(dato, 'puedeCancelar'),
    contactoConsultorio: leerTexto(dato, 'contactoConsultorio'),
  };
}

export function listarPrestacionesPublicas(): Promise<PrestacionPublica[]> {
  return pedir('GET', '/api/public/prestaciones', { parsear: (d) => parsearLista(d, parsearPrestacionPublica) });
}

/** Horarios libres de un solo dia (`desde = hasta`). */
export function obtenerDisponibilidad(prestacionId: number, fecha: string): Promise<Disponibilidad> {
  const consulta = new URLSearchParams({ prestacionId: String(prestacionId), desde: fecha, hasta: fecha });
  return pedir('GET', '/api/public/disponibilidad?' + consulta.toString(), { parsear: parsearDisponibilidad });
}

export function reservarTurno(body: ReservaPublicaRequest): Promise<TurnoResumenPublico> {
  return pedir('POST', '/api/public/turnos', { body, parsear: parsearResumen });
}
