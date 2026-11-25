import type { BloqueoRequest } from '../../api/types';
import { aInstante, diasInclusive, hoyBA, sumarDias } from './fechas';

export const MOTIVO_MAX = 200;
export const RANGO_MAX_DIAS = 31;

export interface EntradaBloqueo {
  readonly inicio: string;
  readonly fin: string;
  readonly motivo: string;
}

export type ErroresBloqueo = Partial<Record<'inicio' | 'fin' | 'motivo', string>>;

export type ResultadoBloqueo =
  | { readonly ok: true; readonly valor: BloqueoRequest }
  | { readonly ok: false; readonly errores: ErroresBloqueo };

/** Valida el formulario (valores `datetime-local` en hora de Buenos Aires) y arma el request del contrato. */
export function validarBloqueo(entrada: EntradaBloqueo): ResultadoBloqueo {
  const errores: { inicio?: string; fin?: string; motivo?: string } = {};
  const inicio = aInstante(entrada.inicio);
  const fin = aInstante(entrada.fin);
  if (inicio === null) {
    errores.inicio = 'Ingresá la fecha y hora de inicio.';
  }
  if (fin === null) {
    errores.fin = 'Ingresá la fecha y hora de fin.';
  }
  if (inicio !== null && fin !== null && Date.parse(inicio) >= Date.parse(fin)) {
    errores.fin = 'La hora de inicio debe ser anterior a la de fin.';
  }
  const motivo = entrada.motivo.trim();
  if (motivo.length > MOTIVO_MAX) {
    errores.motivo = `El motivo no puede superar los ${MOTIVO_MAX} caracteres.`;
  }
  if (inicio === null || fin === null || errores.fin !== undefined || errores.motivo !== undefined) {
    return { ok: false, errores };
  }
  return { ok: true, valor: motivo === '' ? { inicio, fin } : { inicio, fin, motivo } };
}

export interface Rango {
  readonly desde: string;
  readonly hasta: string;
}

export type ErroresRango = Partial<Record<keyof Rango, string>>;

export type ResultadoRango =
  | { readonly ok: true; readonly valor: Rango }
  | { readonly ok: false; readonly errores: ErroresRango };

/** El listado del contrato pide `desde` y `hasta` (`YYYY-MM-DD`), con un maximo de 31 dias inclusive. */
export function validarRango(rango: Rango): ResultadoRango {
  const errores: { desde?: string; hasta?: string } = {};
  if (rango.desde === '') {
    errores.desde = 'Ingresá la fecha desde.';
  }
  if (rango.hasta === '') {
    errores.hasta = 'Ingresá la fecha hasta.';
  }
  if (errores.desde === undefined && errores.hasta === undefined) {
    const dias = diasInclusive(rango.desde, rango.hasta);
    if (dias < 1) {
      errores.hasta = 'La fecha hasta no puede ser anterior a la fecha desde.';
    } else if (dias > RANGO_MAX_DIAS) {
      errores.hasta = `El rango no puede superar los ${RANGO_MAX_DIAS} días.`;
    }
  }
  if (errores.desde !== undefined || errores.hasta !== undefined) {
    return { ok: false, errores };
  }
  return { ok: true, valor: rango };
}

/** Hoy y los 30 dias siguientes (31 dias inclusive, el maximo del contrato), en Buenos Aires. */
export function rangoPorDefecto(ahora: Date = new Date()): Rango {
  const desde = hoyBA(ahora);
  return { desde, hasta: sumarDias(desde, RANGO_MAX_DIAS - 1) };
}
