import { ErrorApi, pedir } from '../../api/cliente';
import type {
  Bloqueo,
  BloqueoCrearRequest,
  BloqueoCreado,
  BloqueoRequest,
  CanceladoPor,
  EstadoTurno,
  Turno,
} from '../../api/types';
import type { Rango } from './bloqueos.logica';
import { esObjeto, leerBooleano, leerNumero, leerTexto, leerTextoONulo, parsearLista, sinContenido } from './parseo';

const RUTA = '/api/bloqueos';

const ESTADOS: readonly EstadoTurno[] = ['PENDIENTE', 'CONFIRMADO', 'CANCELADO', 'ASISTIO', 'AUSENTE'];

function leerObjeto(o: Record<string, unknown>, campo: string): Record<string, unknown> {
  const v = o[campo];
  if (!esObjeto(v)) {
    throw new Error('Respuesta inválida: ' + campo);
  }
  return v;
}

function parsearBloqueo(dato: unknown): Bloqueo {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: bloqueo');
  }
  return {
    id: leerNumero(dato, 'id'),
    inicio: leerTexto(dato, 'inicio'),
    fin: leerTexto(dato, 'fin'),
    motivo: leerTextoONulo(dato, 'motivo'),
    creadoEn: leerTexto(dato, 'creadoEn'),
  };
}

function leerEstado(o: Record<string, unknown>): EstadoTurno {
  const estado = ESTADOS.find((e) => e === o['estado']);
  if (estado === undefined) {
    throw new Error('Respuesta inválida: estado');
  }
  return estado;
}

function leerCanceladoPor(o: Record<string, unknown>): CanceladoPor | null {
  const v = o['canceladoPor'];
  if (v === null) {
    return null;
  }
  if (v === 'PACIENTE' || v === 'ODONTOLOGO') {
    return v;
  }
  throw new Error('Respuesta inválida: canceladoPor');
}

function parsearTurno(dato: unknown): Turno {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: turno');
  }
  const paciente = leerObjeto(dato, 'paciente');
  const prestacion = leerObjeto(dato, 'prestacion');
  return {
    id: leerNumero(dato, 'id'),
    inicio: leerTexto(dato, 'inicio'),
    fin: leerTexto(dato, 'fin'),
    estado: leerEstado(dato),
    canceladoPor: leerCanceladoPor(dato),
    paciente: {
      id: leerNumero(paciente, 'id'),
      nombre: leerTexto(paciente, 'nombre'),
      apellido: leerTexto(paciente, 'apellido'),
      dni: leerTexto(paciente, 'dni'),
    },
    prestacion: {
      id: leerNumero(prestacion, 'id'),
      nombre: leerTexto(prestacion, 'nombre'),
      duracionMinutos: leerNumero(prestacion, 'duracionMinutos'),
    },
    emailContacto: leerTexto(dato, 'emailContacto'),
    telefonoContacto: leerTexto(dato, 'telefonoContacto'),
    consentimientoAt: leerTextoONulo(dato, 'consentimientoAt'),
    pendienteMenos24h: leerBooleano(dato, 'pendienteMenos24h'),
    creadoEn: leerTexto(dato, 'creadoEn'),
  };
}

function parsearCreado(dato: unknown): BloqueoCreado {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: bloqueo creado');
  }
  const turnos = dato['turnosCancelados'];
  return { bloqueo: parsearBloqueo(dato['bloqueo']), turnosCancelados: parsearLista(turnos, parsearTurno) };
}

function parsearPrevisualizacion(dato: unknown): Turno[] {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: previsualización');
  }
  return parsearLista(dato['turnosAfectados'], parsearTurno);
}

export function listarBloqueos(rango: Rango): Promise<Bloqueo[]> {
  const consulta = `?desde=${encodeURIComponent(rango.desde)}&hasta=${encodeURIComponent(rango.hasta)}`;
  return pedir('GET', RUTA + consulta, { parsear: (d) => parsearLista(d, parsearBloqueo) });
}

export function previsualizarBloqueo(body: BloqueoRequest): Promise<Turno[]> {
  return pedir('POST', RUTA + '/previsualizar', { body, parsear: parsearPrevisualizacion });
}

export function crearBloqueo(body: BloqueoCrearRequest): Promise<BloqueoCreado> {
  return pedir('POST', RUTA, { body, parsear: parsearCreado });
}

export function eliminarBloqueo(id: number): Promise<void> {
  return pedir('DELETE', RUTA + '/' + id, { parsear: sinContenido });
}

/** Turnos del 409 `BLOQUEO_CON_TURNOS_AFECTADOS` (`ErrorBloqueoConflicto`); `null` si el error es otro. */
export function turnosAfectadosDe(error: unknown): Turno[] | null {
  if (!(error instanceof ErrorApi) || error.codigo !== 'BLOQUEO_CON_TURNOS_AFECTADOS') {
    return null;
  }
  const cuerpo: unknown = error.cuerpo;
  if (!esObjeto(cuerpo) || !Array.isArray(cuerpo['turnosAfectados'])) {
    return null;
  }
  try {
    return parsearLista(cuerpo['turnosAfectados'], parsearTurno);
  } catch {
    return null;
  }
}
