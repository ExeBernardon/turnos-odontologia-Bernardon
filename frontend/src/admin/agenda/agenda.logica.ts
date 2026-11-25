import type { Bloqueo, EstadoTurno, HorarioAtencion, Turno } from '../../api/types';
import { sumarDias } from '../configuracion/fechas';

export const ZONA_AGENDA = 'America/Argentina/Buenos_Aires';
export const VENTANA_DESDE = 8 * 60;
export const VENTANA_HASTA = 20 * 60;

const ISO = new Intl.DateTimeFormat('en-US', {
  timeZone: ZONA_AGENDA,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});
const HORA = new Intl.DateTimeFormat('es-AR', {
  timeZone: ZONA_AGENDA,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const LARGA = new Intl.DateTimeFormat('es-AR', {
  timeZone: ZONA_AGENDA,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});
const CORTA = new Intl.DateTimeFormat('es-AR', {
  timeZone: ZONA_AGENDA,
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

function partes(instante: Date, formato: Intl.DateTimeFormat): Record<string, string> {
  const resultado: Record<string, string> = {};
  for (const parte of formato.formatToParts(instante)) {
    resultado[parte.type] = parte.value;
  }
  return resultado;
}

function dosDigitos(valor: number): string {
  return String(valor).padStart(2, '0');
}

export function fechaDeInstante(instante: string): string | null {
  const fecha = new Date(instante);
  if (Number.isNaN(fecha.getTime())) {
    return null;
  }
  const p = partes(fecha, ISO);
  const anio = p['year'];
  const mes = p['month'];
  const dia = p['day'];
  if (anio === undefined || mes === undefined || dia === undefined) {
    return null;
  }
  return `${anio}-${mes}-${dia}`;
}

export function formatearHora(instante: string): string {
  const fecha = new Date(instante);
  return Number.isNaN(fecha.getTime()) ? '' : HORA.format(fecha);
}

export function minutosDeInstante(instante: string): number | null {
  const fecha = new Date(instante);
  if (Number.isNaN(fecha.getTime())) {
    return null;
  }
  const p = partes(fecha, HORA);
  const hora = p['hour'];
  const minuto = p['minute'];
  if (hora === undefined || minuto === undefined) {
    return null;
  }
  return Number(hora) * 60 + Number(minuto);
}

export function horaDesdeMinutos(minutos: number): string {
  if (!Number.isFinite(minutos)) {
    return '--:--';
  }
  return `${dosDigitos(Math.floor(minutos / 60))}:${dosDigitos(minutos % 60)}`;
}

export function formatearFechaLarga(fecha: string): string {
  const instante = new Date(`${fecha}T12:00:00Z`);
  return Number.isNaN(instante.getTime()) ? fecha : LARGA.format(instante);
}

export function etiquetaDia(fecha: string): string {
  const instante = new Date(`${fecha}T12:00:00Z`);
  return Number.isNaN(instante.getTime()) ? fecha : CORTA.format(instante);
}

export function diaSemana(fecha: string): number {
  const dia = new Date(`${fecha}T00:00:00Z`).getUTCDay();
  return dia === 0 ? 7 : dia;
}

export function inicioSemana(fecha: string): string {
  const dia = diaSemana(fecha);
  return dia === 0 ? fecha : sumarDias(fecha, 1 - dia);
}

export function rangoDiario(fecha: string): { readonly desde: string; readonly hasta: string } {
  return { desde: fecha, hasta: fecha };
}

export function rangoSemanal(fecha: string): { readonly desde: string; readonly hasta: string } {
  const desde = inicioSemana(fecha);
  return { desde, hasta: sumarDias(desde, 6) };
}

function aMinutos(hora: string): number | null {
  const coincidencia = /^(\d{2}):(\d{2})$/.exec(hora);
  if (coincidencia === null) {
    return null;
  }
  const horas = Number(coincidencia[1]);
  const minutos = Number(coincidencia[2]);
  if (horas > 23 || minutos > 59) {
    return null;
  }
  return horas * 60 + minutos;
}

export interface RangoMinutos {
  readonly desde: number;
  readonly hasta: number;
}

export interface Franja {
  readonly tipo: 'sin-atencion' | 'bloqueo';
  readonly inicio: string;
  readonly fin: string;
  readonly motivo: string | null;
  readonly inicioMinutos: number;
}

function franja(tipo: Franja['tipo'], desde: number, hasta: number, motivo: string | null = null): Franja {
  return { tipo, inicio: horaDesdeMinutos(desde), fin: horaDesdeMinutos(hasta), motivo, inicioMinutos: desde };
}

export function rangosAtencion(fecha: string, horarios: readonly HorarioAtencion[]): readonly RangoMinutos[] {
  const dia = diaSemana(fecha);
  const rangos: RangoMinutos[] = [];
  for (const horario of horarios) {
    if (horario.diaSemana !== dia) {
      continue;
    }
    const desde = aMinutos(horario.horaInicio);
    const hasta = aMinutos(horario.horaFin);
    if (desde !== null && hasta !== null && desde < hasta) {
      rangos.push({ desde, hasta });
    }
  }
  return rangos.sort((a, b) => a.desde - b.desde);
}

export function huecosSinAtencion(rangos: readonly RangoMinutos[]): readonly Franja[] {
  const huecos: Franja[] = [];
  let cursor = VENTANA_DESDE;
  for (const rango of [...rangos].sort((a, b) => a.desde - b.desde)) {
    const desde = Math.max(VENTANA_DESDE, rango.desde);
    const hasta = Math.min(VENTANA_HASTA, rango.hasta);
    if (hasta <= cursor) {
      continue;
    }
    if (desde > cursor) {
      huecos.push(franja('sin-atencion', cursor, desde));
    }
    cursor = Math.max(cursor, hasta);
    if (cursor >= VENTANA_HASTA) {
      break;
    }
  }
  if (cursor < VENTANA_HASTA) {
    huecos.push(franja('sin-atencion', cursor, VENTANA_HASTA));
  }
  return huecos;
}

export function franjasBloqueo(fecha: string, bloqueos: readonly Bloqueo[]): readonly Franja[] {
  const franjas: Franja[] = [];
  for (const bloqueo of bloqueos) {
    const inicio = fechaDeInstante(bloqueo.inicio);
    const fin = fechaDeInstante(bloqueo.fin);
    if ((inicio !== null && inicio > fecha) || (fin !== null && fin < fecha)) {
      continue;
    }
    const desde = inicio === fecha ? (minutosDeInstante(bloqueo.inicio) ?? VENTANA_DESDE) : VENTANA_DESDE;
    const hasta = fin === fecha ? (minutosDeInstante(bloqueo.fin) ?? VENTANA_HASTA) : VENTANA_HASTA;
    const inicioMinutos = Math.max(desde, VENTANA_DESDE);
    const finMinutos = Math.min(hasta, VENTANA_HASTA);
    if (finMinutos > inicioMinutos) {
      franjas.push(franja('bloqueo', inicioMinutos, finMinutos, bloqueo.motivo));
    }
  }
  return franjas;
}

export function franjasDelDia(
  fecha: string,
  horarios: readonly HorarioAtencion[],
  bloqueos: readonly Bloqueo[],
): readonly Franja[] {
  const rangos = rangosAtencion(fecha, horarios);
  const huecos = rangos.length === 0 ? [] : huecosSinAtencion(rangos);
  return [...huecos, ...franjasBloqueo(fecha, bloqueos)]
    .sort((a, b) => a.inicioMinutos - b.inicioMinutos || (a.tipo === 'bloqueo' ? 0 : 1) - (b.tipo === 'bloqueo' ? 0 : 1));
}

export function agruparTurnos(
  turnos: readonly Turno[],
  fechas: readonly string[],
): ReadonlyMap<string, readonly Turno[]> {
  const porDia = new Map<string, Turno[]>(fechas.map((fecha) => [fecha, []]));
  for (const turno of turnos) {
    const fecha = fechaDeInstante(turno.inicio);
    if (fecha === null) {
      continue;
    }
    const lista = porDia.get(fecha);
    if (lista !== undefined) {
      lista.push(turno);
    }
  }
  for (const lista of porDia.values()) {
    lista.sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio));
  }
  return porDia;
}

export function esUrgente(turno: Turno): boolean {
  return turno.estado === 'PENDIENTE' && turno.pendienteMenos24h;
}

export function turnosVisibles(turnos: readonly Turno[], mostrarCancelados: boolean): readonly Turno[] {
  return turnos.filter((turno) => mostrarCancelados || turno.estado !== 'CANCELADO');
}

const ETIQUETAS: Readonly<Record<EstadoTurno, string>> = {
  PENDIENTE: 'Sin confirmar',
  CONFIRMADO: 'Confirmado',
  CANCELADO: 'Cancelado',
  ASISTIO: 'Asistió',
  AUSENTE: 'Ausente',
};

export function etiquetaEstado(turno: Turno): string {
  return esUrgente(turno) ? 'Sin confirmar · menos de 24 h' : (ETIQUETAS[turno.estado] ?? turno.estado);
}

export function claseEstado(turno: Turno): string {
  if (esUrgente(turno)) {
    return 'agenda-badge--pendiente';
  }
  if (turno.estado === 'CONFIRMADO') {
    return 'agenda-badge--confirmado';
  }
  return 'agenda-badge';
}

export function cantidadUrgentes(turnos: readonly Turno[]): number {
  return turnos.filter(esUrgente).length;
}
