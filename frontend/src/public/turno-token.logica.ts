import { ErrorApi, ErrorConexion, MENSAJE_GENERICO } from '../api/cliente';
import type { TurnoResumenPublico } from '../api/types';

/** Largo del token segun el contrato (`minLength: 16`, `maxLength: 128`). */
const TOKEN_MIN = 16;
const TOKEN_MAX = 128;

/** Token del parametro `token` de la URL, recortado; `null` si falta o su largo esta fuera del contrato. */
export function leerToken(query: URLSearchParams): string | null {
  const token = (query.get('token') ?? '').trim();
  return token.length >= TOKEN_MIN && token.length <= TOKEN_MAX ? token : null;
}

export type AccionToken = 'confirmar' | 'cancelar';

export type Vista =
  | { readonly tipo: 'accionable' }
  | { readonly tipo: 'informativa'; readonly mensaje: string; readonly contacto: boolean; readonly reservarOtro: boolean };

function informativa(mensaje: string, extra: { contacto?: boolean; reservarOtro?: boolean } = {}): Vista {
  return { tipo: 'informativa', mensaje, contacto: extra.contacto ?? false, reservarOtro: extra.reservarOtro ?? false };
}

const TURNO_PASADO = 'Este turno ya pasó.';
const FUERA_DE_PLAZO_CONFIRMAR = 'Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes, con el link del recordatorio.';
const FUERA_DE_PLAZO_CANCELAR = 'Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio.';

function habilitada(resumen: TurnoResumenPublico, accion: AccionToken): boolean {
  return accion === 'confirmar' ? resumen.puedeConfirmar : resumen.puedeCancelar;
}

/**
 * Que mostrar para el turno leido. La habilitacion del boton la decide solo el backend (`puedeConfirmar` /
 * `puedeCancelar`); el front compara `inicio` con `ahora` unicamente para redactar el mensaje.
 */
export function vistaDeTurno(resumen: TurnoResumenPublico, accion: AccionToken, ahora: Date): Vista {
  if (habilitada(resumen, accion)) {
    return { tipo: 'accionable' };
  }
  const pasado = new Date(resumen.inicio).getTime() <= ahora.getTime();
  switch (resumen.estado) {
    case 'CANCELADO':
      return accion === 'confirmar'
        ? informativa('Este turno está cancelado y no se puede confirmar.', { reservarOtro: true })
        : informativa('Tu turno ya estaba cancelado.', { reservarOtro: true });
    case 'ASISTIO':
    case 'AUSENTE':
      return informativa(TURNO_PASADO);
    case 'CONFIRMADO':
      if (pasado) {
        return informativa(TURNO_PASADO);
      }
      return accion === 'confirmar'
        ? informativa('Tu turno ya estaba confirmado.')
        : informativa('Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio.', { contacto: true });
    case 'PENDIENTE':
      if (pasado) {
        return informativa(TURNO_PASADO);
      }
      return accion === 'confirmar'
        ? informativa(FUERA_DE_PLAZO_CONFIRMAR)
        : informativa(FUERA_DE_PLAZO_CANCELAR, { contacto: true });
    default: {
      const inesperado: never = resumen.estado;
      return inesperado;
    }
  }
}

export type EfectoErrorToken = 'linkInvalido' | 'releer' | 'reintentar';

export interface AccionErrorToken {
  readonly mensaje: string;
  readonly efecto: EfectoErrorToken;
}

export const MENSAJE_LINK_INVALIDO = 'El link no es válido.';

/** Mensaje fijo del front (nunca el del servidor) y efecto a aplicar en la pantalla ante un error de la API. */
export function accionDeErrorToken(error: unknown, accion: AccionToken): AccionErrorToken {
  if (error instanceof ErrorConexion) {
    return { mensaje: error.message, efecto: 'reintentar' };
  }
  if (error instanceof ErrorApi) {
    if (error.status === 404 || error.status === 400) {
      return { mensaje: MENSAJE_LINK_INVALIDO, efecto: 'linkInvalido' };
    }
    switch (error.codigo) {
      case 'CONFIRMACION_FUERA_DE_PLAZO':
        return { mensaje: FUERA_DE_PLAZO_CONFIRMAR, efecto: 'releer' };
      case 'CANCELACION_FUERA_DE_PLAZO':
        return { mensaje: FUERA_DE_PLAZO_CANCELAR, efecto: 'releer' };
      case 'TRANSICION_INVALIDA':
        return { mensaje: `El turno cambió y ya no se puede ${accion === 'confirmar' ? 'confirmar' : 'cancelar'} desde el link.`, efecto: 'releer' };
      default:
        break;
    }
  }
  return { mensaje: MENSAJE_GENERICO, efecto: 'reintentar' };
}
