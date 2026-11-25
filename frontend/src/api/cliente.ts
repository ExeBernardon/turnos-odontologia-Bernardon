import { obtenerToken } from '../shared/sesion';
import type { ApiError, CodigoError, DetalleError } from './types';

/** Error de negocio o de request devuelto por la API (formato uniforme de docs/api/errores.md). */
export class ErrorApi extends Error {
  readonly status: number;
  /** `null` cuando la respuesta de error no tiene el formato uniforme (proxy caido, HTML, etc.). */
  readonly codigo: CodigoError | null;
  readonly detalles: readonly DetalleError[] | undefined;
  readonly cuerpo: ApiError | null;

  constructor(status: number, mensaje: string, codigo: CodigoError | null, detalles?: readonly DetalleError[], cuerpo?: ApiError) {
    super(mensaje);
    this.name = 'ErrorApi';
    this.status = status;
    this.codigo = codigo;
    this.detalles = detalles;
    this.cuerpo = cuerpo ?? null;
  }
}

/** El request no llego al servidor (red caida, backend apagado). */
export class ErrorConexion extends Error {
  constructor() {
    super('No pudimos conectarnos con el servidor. Revisá tu conexión e intentá de nuevo.');
    this.name = 'ErrorConexion';
  }
}

export const MENSAJE_GENERICO = 'No se pudo completar la operación. Intentá de nuevo en unos minutos.';

type ManejadorExpiracion = () => void;

let manejadorExpiracion: ManejadorExpiracion | null = null;

export function registrarManejadorExpiracion(manejador: ManejadorExpiracion | null): void {
  manejadorExpiracion = manejador;
}

const CODIGOS: ReadonlySet<string> = new Set<CodigoError>([
  'VALIDACION',
  'SOLICITUD_MAL_FORMADA',
  'NO_AUTENTICADO',
  'CREDENCIALES_INVALIDAS',
  'RECURSO_NO_ENCONTRADO',
  'TOKEN_INVALIDO',
  'HORARIO_OCUPADO',
  'BLOQUEO_CON_TURNOS_AFECTADOS',
  'HORARIO_NO_DISPONIBLE',
  'CONSENTIMIENTO_REQUERIDO',
  'CANCELACION_FUERA_DE_PLAZO',
  'CONFIRMACION_FUERA_DE_PLAZO',
  'TRANSICION_INVALIDA',
  'PRESTACION_INACTIVA',
  'PRESTACION_CON_TURNOS',
  'DNI_DUPLICADO',
  'RANGO_HORARIO_INVALIDO',
  'HORARIOS_SOLAPADOS',
  'ERROR_INTERNO',
]);

function esObjeto(dato: unknown): dato is Record<string, unknown> {
  return typeof dato === 'object' && dato !== null;
}

function esCodigoError(dato: unknown): dato is CodigoError {
  return typeof dato === 'string' && CODIGOS.has(dato);
}

/** Type guard del cuerpo de error uniforme: el cuerpo recibido es `unknown` hasta pasar por aca. */
export function esApiError(dato: unknown): dato is ApiError {
  return (
    esObjeto(dato) &&
    typeof dato['status'] === 'number' &&
    esCodigoError(dato['codigo']) &&
    typeof dato['mensaje'] === 'string' &&
    typeof dato['path'] === 'string'
  );
}

export interface OpcionesPedido<T> {
  readonly body?: unknown;
  /** Valida y convierte el cuerpo recibido (`unknown`) al tipo del recurso. Lo aporta cada recurso. */
  readonly parsear: (dato: unknown) => T;
}

async function leerJson(respuesta: Response): Promise<unknown> {
  const texto = await respuesta.text();
  if (texto === '') {
    return undefined;
  }
  try {
    return JSON.parse(texto) as unknown;
  } catch {
    return undefined;
  }
}

export async function pedir<T>(metodo: string, ruta: string, opciones: OpcionesPedido<T>): Promise<T> {
  const headers = new Headers({ Accept: 'application/json' });
  const token = obtenerToken();
  if (token !== null) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  const init: RequestInit = { method: metodo, headers };
  if (opciones.body !== undefined) {
    headers.set('Content-Type', 'application/json');
    init.body = JSON.stringify(opciones.body);
  }

  let respuesta: Response;
  try {
    respuesta = await fetch(ruta, init);
  } catch {
    throw new ErrorConexion();
  }

  const dato = await leerJson(respuesta);
  if (!respuesta.ok) {
    const error = esApiError(dato)
      ? new ErrorApi(respuesta.status, dato.mensaje, dato.codigo, dato.detalles, dato)
      : new ErrorApi(respuesta.status, MENSAJE_GENERICO, null);
    if (respuesta.status === 401 && error.codigo === 'NO_AUTENTICADO') {
      manejadorExpiracion?.();
    }
    throw error;
  }
  return opciones.parsear(dato);
}
