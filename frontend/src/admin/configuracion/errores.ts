import { ErrorApi, ErrorConexion, MENSAJE_GENERICO } from '../../api/cliente';
import type { CodigoError } from '../../api/types';

const MENSAJES: Partial<Record<CodigoError, string>> = {
  VALIDACION: 'Hay campos con errores. Revisalos e intentá de nuevo.',
  SOLICITUD_MAL_FORMADA: 'La solicitud no es válida. Revisá los datos e intentá de nuevo.',
  NO_AUTENTICADO: 'Tu sesión expiró o no es válida. Volvé a iniciar sesión.',
  RECURSO_NO_ENCONTRADO: 'El elemento ya no existe. Actualizá la lista e intentá de nuevo.',
  PRESTACION_CON_TURNOS: 'La prestación tiene turnos y no se puede eliminar. Podés desactivarla en su lugar.',
  RANGO_HORARIO_INVALIDO: 'La hora de inicio debe ser anterior a la hora de fin.',
  HORARIOS_SOLAPADOS: 'El rango se superpone con otro horario de atención del mismo día.',
  ERROR_INTERNO: MENSAJE_GENERICO,
};

/** Mensaje en español para mostrar al usuario; nunca expone detalles internos. */
export function mensajeDeError(error: unknown): string {
  if (error instanceof ErrorConexion) {
    return error.message;
  }
  if (error instanceof ErrorApi && error.codigo !== null) {
    return MENSAJES[error.codigo] ?? MENSAJE_GENERICO;
  }
  return MENSAJE_GENERICO;
}

/** Errores por campo de un 400 VALIDACION (`detalles`); vacio en cualquier otro caso. */
export function detallesPorCampo(error: unknown): Record<string, string> {
  const resultado: Record<string, string> = {};
  if (error instanceof ErrorApi && error.codigo === 'VALIDACION') {
    for (const d of error.detalles ?? []) {
      resultado[d.campo] ??= d.mensaje;
    }
  }
  return resultado;
}
