import { pedir } from '../api/cliente';
import type { TurnoResumenPublico } from '../api/types';
import { parsearResumen } from './reservar.api';

/** El token viaja codificado: un valor manipulado (`/`, `?`, `..`) no puede cambiar la ruta del pedido. */
function ruta(token: string, sufijo = ''): string {
  return `/api/public/turnos/${encodeURIComponent(token)}${sufijo}`;
}

/** Lee el turno del link; no cambia su estado. */
export function obtenerTurnoPorToken(token: string): Promise<TurnoResumenPublico> {
  return pedir('GET', ruta(token), { parsear: parsearResumen });
}

export function confirmarTurnoPorToken(token: string): Promise<TurnoResumenPublico> {
  return pedir('POST', ruta(token, '/confirmar'), { parsear: parsearResumen });
}

export function cancelarTurnoPorToken(token: string): Promise<TurnoResumenPublico> {
  return pedir('POST', ruta(token, '/cancelar'), { parsear: parsearResumen });
}
