import type { HorarioAtencion, HorarioAtencionRequest } from '../../api/types';

export const DIAS: readonly string[] = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

export interface EntradaRango {
  readonly diaSemana: string;
  readonly horaInicio: string;
  readonly horaFin: string;
}

export type ErroresRango = Partial<Record<'diaSemana' | 'horaInicio' | 'horaFin', string>>;

export type ResultadoRango =
  | { readonly ok: true; readonly valor: HorarioAtencionRequest }
  | { readonly ok: false; readonly errores: ErroresRango };

export interface GrupoDia {
  readonly dia: number;
  readonly nombre: string;
  readonly rangos: readonly HorarioAtencion[];
}

const HORA = /^([01]\d|2[0-3]):[0-5]\d$/;

export function validarRango(entrada: EntradaRango): ResultadoRango {
  const errores: { diaSemana?: string; horaInicio?: string; horaFin?: string } = {};
  const dia = /^\d+$/.test(entrada.diaSemana) ? Number(entrada.diaSemana) : Number.NaN;
  if (!Number.isInteger(dia) || dia < 1 || dia > 7) {
    errores.diaSemana = 'Elegí un día de la semana.';
  }
  if (!HORA.test(entrada.horaInicio)) {
    errores.horaInicio = 'Indicá la hora de inicio.';
  }
  if (!HORA.test(entrada.horaFin)) {
    errores.horaFin = 'Indicá la hora de fin.';
  } else if (errores.horaInicio === undefined && entrada.horaInicio >= entrada.horaFin) {
    // HH:mm con cero a la izquierda: la comparacion de strings equivale a la de horas.
    errores.horaFin = 'La hora de fin debe ser posterior a la de inicio.';
  }
  if (errores.diaSemana !== undefined || errores.horaInicio !== undefined || errores.horaFin !== undefined) {
    return { ok: false, errores };
  }
  return { ok: true, valor: { diaSemana: dia, horaInicio: entrada.horaInicio, horaFin: entrada.horaFin } };
}

/** Siete grupos (lunes a domingo) con sus rangos ordenados por hora de inicio. */
export function agruparPorDia(horarios: readonly HorarioAtencion[]): GrupoDia[] {
  return DIAS.map((nombre, i) => ({
    dia: i + 1,
    nombre,
    rangos: horarios.filter((h) => h.diaSemana === i + 1).sort((a, b) => a.horaInicio.localeCompare(b.horaInicio)),
  }));
}
