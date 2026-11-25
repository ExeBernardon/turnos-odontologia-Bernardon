/**
 * Fechas en hora de Buenos Aires. Argentina no usa horario de verano (offset fijo -03:00), por lo que
 * un `datetime-local` se convierte agregando el offset. Si eso cambiara, solo este archivo se ajusta.
 */

export const ZONA = 'America/Argentina/Buenos_Aires';
const OFFSET = '-03:00';
const OFFSET_MS = 3 * 60 * 60 * 1000;
const DIA_MS = 24 * 60 * 60 * 1000;

const LOCAL = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/;

/** `YYYY-MM-DDTHH:mm[:ss]` (hora de Buenos Aires) a ISO-8601 con offset; `null` si no es valido. */
export function aInstante(local: string): string | null {
  const m = LOCAL.exec(local);
  if (m === null) {
    return null;
  }
  const [anio, mes, dia, hora, minuto, segundo] = m.slice(1).map((p) => (p === undefined ? 0 : Number(p)));
  if (
    anio === undefined || mes === undefined || dia === undefined ||
    hora === undefined || minuto === undefined || segundo === undefined
  ) {
    return null;
  }
  const valida = new Date(Date.UTC(anio, mes - 1, dia));
  if (
    valida.getUTCFullYear() !== anio || valida.getUTCMonth() !== mes - 1 || valida.getUTCDate() !== dia ||
    hora > 23 || minuto > 59 || segundo > 59
  ) {
    return null;
  }
  const dosDigitos = (n: number): string => String(n).padStart(2, '0');
  return `${m[1] ?? ''}-${m[2] ?? ''}-${m[3] ?? ''}T${dosDigitos(hora)}:${dosDigitos(minuto)}:${dosDigitos(segundo)}${OFFSET}`;
}

const FORMATO = new Intl.DateTimeFormat('es-AR', {
  timeZone: ZONA,
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** Instante ISO-8601 formateado en hora de Buenos Aires (ej. `24/12/2026, 09:00`). */
export function formatear(instante: string): string {
  const fecha = new Date(instante);
  return Number.isNaN(fecha.getTime()) ? instante : FORMATO.format(fecha);
}

/** Fecha de hoy en Buenos Aires como `YYYY-MM-DD`. */
export function hoyBA(ahora: Date = new Date()): string {
  return new Date(ahora.getTime() - OFFSET_MS).toISOString().slice(0, 10);
}

/** Suma (o resta) dias a una fecha `YYYY-MM-DD`. */
export function sumarDias(fecha: string, dias: number): string {
  return new Date(Date.parse(fecha + 'T00:00:00Z') + dias * DIA_MS).toISOString().slice(0, 10);
}

/** Cantidad de dias entre dos fechas contando ambos extremos (<= 0 si `hasta` < `desde`). */
export function diasInclusive(desde: string, hasta: string): number {
  return Math.round((Date.parse(hasta + 'T00:00:00Z') - Date.parse(desde + 'T00:00:00Z')) / DIA_MS) + 1;
}
