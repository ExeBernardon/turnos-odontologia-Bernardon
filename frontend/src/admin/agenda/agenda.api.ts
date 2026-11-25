import { ErrorApi, pedir } from '../../api/cliente';
import type { Bloqueo, CanceladoPor, EstadoTurno, HorarioAtencion, Turno } from '../../api/types';
import type { Rango } from '../configuracion/bloqueos.logica';
import { esObjeto, leerBooleano, leerNumero, leerTexto, leerTextoONulo } from '../configuracion/parseo';

const ESTADOS: readonly EstadoTurno[] = ['PENDIENTE', 'CONFIRMADO', 'CANCELADO', 'ASISTIO', 'AUSENTE'];

export class ErrorRespuestaAgenda extends Error {
  constructor(campo: string) {
    super(`Respuesta inválida: ${campo}`);
    this.name = 'ErrorRespuestaAgenda';
  }
}

export interface DatosAgenda {
  readonly turnos: readonly Turno[] | null;
  readonly bloqueos: readonly Bloqueo[] | null;
  readonly horarios: readonly HorarioAtencion[] | null;
  readonly errores: readonly unknown[];
}

function leerObjeto(o: Record<string, unknown>, campo: string): Record<string, unknown> {
  const valor = o[campo];
  if (!esObjeto(valor)) {
    throw new ErrorRespuestaAgenda(campo);
  }
  return valor;
}

export function parsearTurno(dato: unknown): Turno | null {
  try {
    if (!esObjeto(dato)) {
      return null;
    }
    const paciente = leerObjeto(dato, 'paciente');
    const prestacion = leerObjeto(dato, 'prestacion');
    const estado = ESTADOS.find((valor) => valor === dato['estado']);
    if (estado === undefined) {
      return null;
    }
    const canceladoPor = dato['canceladoPor'];
    if (canceladoPor !== null && canceladoPor !== 'PACIENTE' && canceladoPor !== 'ODONTOLOGO') {
      return null;
    }
    return {
      id: leerNumero(dato, 'id'),
      inicio: leerTexto(dato, 'inicio'),
      fin: leerTexto(dato, 'fin'),
      estado,
      canceladoPor: canceladoPor as CanceladoPor | null,
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
  } catch {
    return null;
  }
}

function parsearBloqueo(dato: unknown): Bloqueo | null {
  try {
    if (!esObjeto(dato)) {
      return null;
    }
    return {
      id: leerNumero(dato, 'id'),
      inicio: leerTexto(dato, 'inicio'),
      fin: leerTexto(dato, 'fin'),
      motivo: leerTextoONulo(dato, 'motivo'),
      creadoEn: leerTexto(dato, 'creadoEn'),
    };
  } catch {
    return null;
  }
}

function parsearHorario(dato: unknown): HorarioAtencion | null {
  try {
    if (!esObjeto(dato)) {
      return null;
    }
    return {
      id: leerNumero(dato, 'id'),
      diaSemana: leerNumero(dato, 'diaSemana'),
      horaInicio: leerTexto(dato, 'horaInicio'),
      horaFin: leerTexto(dato, 'horaFin'),
    };
  } catch {
    return null;
  }
}

function parsearListaSegura<T>(dato: unknown, item: (valor: unknown) => T | null): readonly T[] {
  if (!Array.isArray(dato)) {
    throw new ErrorRespuestaAgenda('se esperaba una lista');
  }
  const resultado: T[] = [];
  for (const valor of dato) {
    const parseado = item(valor);
    if (parseado !== null) {
      resultado.push(parseado);
    }
  }
  return resultado;
}

function consultaRango(rango: Rango): string {
  return `?desde=${encodeURIComponent(rango.desde)}&hasta=${encodeURIComponent(rango.hasta)}`;
}

export function listarTurnosAgenda(rango: Rango): Promise<readonly Turno[]> {
  return pedir('GET', `/api/turnos${consultaRango(rango)}`, {
    parsear: (dato) => parsearListaSegura(dato, parsearTurno),
  });
}

export function listarBloqueosAgenda(rango: Rango): Promise<readonly Bloqueo[]> {
  return pedir('GET', `/api/bloqueos${consultaRango(rango)}`, {
    parsear: (dato) => parsearListaSegura(dato, parsearBloqueo),
  });
}

export function listarHorariosAgenda(): Promise<readonly HorarioAtencion[]> {
  return pedir('GET', '/api/horarios-atencion', {
    parsear: (dato) => parsearListaSegura(dato, parsearHorario),
  });
}

function esNoAutenticado(error: unknown): error is ErrorApi {
  return error instanceof ErrorApi && error.status === 401 && error.codigo === 'NO_AUTENTICADO';
}

export async function cargarDatosAgenda(rango: Rango): Promise<DatosAgenda> {
  const [turnos, bloqueos, horarios] = await Promise.allSettled([
    listarTurnosAgenda(rango),
    listarBloqueosAgenda(rango),
    listarHorariosAgenda(),
  ]);

  const resultados = [turnos, bloqueos, horarios];
  const errores: unknown[] = [];
  for (const resultado of resultados) {
    if (resultado.status === 'rejected') {
      errores.push(resultado.reason);
    }
  }
  const noAutenticado = errores.find(esNoAutenticado);
  if (noAutenticado !== undefined) {
    throw noAutenticado;
  }

  return {
    turnos: turnos.status === 'fulfilled' ? turnos.value : null,
    bloqueos: bloqueos.status === 'fulfilled' ? bloqueos.value : null,
    horarios: horarios.status === 'fulfilled' ? horarios.value : null,
    errores,
  };
}
