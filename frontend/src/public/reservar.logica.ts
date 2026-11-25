import { ErrorApi, ErrorConexion, MENSAJE_GENERICO } from '../api/cliente';
import type { EstadoTurno, ReservaPublicaRequest } from '../api/types';
import { ZONA, hoyBA, sumarDias } from '../admin/configuracion/fechas';
import { validarPaciente } from '../admin/pacientes.logica';

/** Replica `APP_TURNO_VENTANA_MAX_DIAS` (default del backend): hasta cuantos dias adelante se puede reservar. */
export const VENTANA_MAX_DIAS = 60;

const EMAIL_MAX = 150;
const EMAIL_VALIDO = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const TELEFONO_VALIDO = /^[0-9+()\-\s]{6,30}$/;
const FECHA_DIA = /^\d{4}-\d{2}-\d{2}$/;

export interface VentanaFechas {
  readonly min: string;
  readonly max: string;
}

/** Rango de fechas elegibles (`YYYY-MM-DD`, calendario de Buenos Aires): de hoy a hoy + 60 dias. */
export function ventanaFechas(ahora: Date): VentanaFechas {
  const min = hoyBA(ahora);
  return { min, max: sumarDias(min, VENTANA_MAX_DIAS) };
}

function esFechaReal(fecha: string): boolean {
  if (!FECHA_DIA.test(fecha)) {
    return false;
  }
  const t = Date.parse(fecha + 'T00:00:00Z');
  return !Number.isNaN(t) && new Date(t).toISOString().slice(0, 10) === fecha;
}

/** Mensaje de error de la fecha elegida, o `undefined` si es valida y esta dentro de la ventana. */
export function validarFecha(fecha: string, ventana: VentanaFechas): string | undefined {
  if (fecha === '') {
    return 'Elegí una fecha.';
  }
  if (!esFechaReal(fecha)) {
    return 'La fecha no es válida.';
  }
  if (fecha < ventana.min || fecha > ventana.max) {
    return 'Elegí una fecha entre hoy y los próximos ' + VENTANA_MAX_DIAS + ' días.';
  }
  return undefined;
}

const FORMATO_HORA = new Intl.DateTimeFormat('es-AR', { timeZone: ZONA, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const FORMATO_FECHA = new Intl.DateTimeFormat('es-AR', {
  timeZone: ZONA,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

/** `HH:mm` en hora de Buenos Aires; cadena vacia si el instante no es valido. */
export function formatearHora(instante: string): string {
  const fecha = new Date(instante);
  return Number.isNaN(fecha.getTime()) ? '' : FORMATO_HORA.format(fecha);
}

/** Fecha en palabras (`jueves 10 de diciembre de 2026`) en Buenos Aires; vacia si el instante no es valido. */
export function formatearFechaTurno(instante: string): string {
  const fecha = new Date(instante);
  if (Number.isNaN(fecha.getTime())) {
    return '';
  }
  const partes = Object.fromEntries(FORMATO_FECHA.formatToParts(fecha).map((p) => [p.type, p.value]));
  return `${partes['weekday'] ?? ''} ${partes['day'] ?? ''} de ${partes['month'] ?? ''} de ${partes['year'] ?? ''}`;
}

export interface EntradaReserva {
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
  readonly email: string;
  readonly telefono: string;
  readonly consentimiento: boolean;
}

export type ErroresReserva = Partial<Record<keyof EntradaReserva, string>>;

/** Valida los datos del paciente con las reglas del contrato; devuelve un mensaje por campo con error. */
export function validarReserva(entrada: EntradaReserva): ErroresReserva {
  const errores: { -readonly [K in keyof EntradaReserva]?: string } = { ...validarPaciente(entrada) };
  const email = entrada.email.trim();
  if (email === '') {
    errores.email = 'El email es obligatorio.';
  } else if (email.length > EMAIL_MAX) {
    errores.email = `El email no puede superar los ${EMAIL_MAX} caracteres.`;
  } else if (!EMAIL_VALIDO.test(email)) {
    errores.email = 'Ingresá un email válido (ej. nombre@ejemplo.com).';
  }
  if (!TELEFONO_VALIDO.test(entrada.telefono.trim())) {
    errores.telefono = 'El teléfono debe tener entre 6 y 30 caracteres: números, espacios, +, - y paréntesis.';
  }
  if (!entrada.consentimiento) {
    errores.consentimiento = 'Tenés que aceptar el tratamiento de tus datos para reservar.';
  }
  return errores;
}

/** Arma el body del contrato: textos recortados, DNI como se escribio e `inicio` tal como vino de la API. */
export function armarReserva(entrada: EntradaReserva, prestacionId: number, inicio: string): ReservaPublicaRequest {
  return {
    nombre: entrada.nombre.trim(),
    apellido: entrada.apellido.trim(),
    dni: entrada.dni.trim(),
    email: entrada.email.trim(),
    telefono: entrada.telefono.trim(),
    prestacionId,
    inicio,
    consentimiento: true,
  };
}

/** Estado del turno en palabras para el paciente. */
export function textoEstado(estado: EstadoTurno): string {
  switch (estado) {
    case 'PENDIENTE':
      return 'Pendiente de confirmación';
    case 'CONFIRMADO':
      return 'Confirmado';
    case 'CANCELADO':
      return 'Cancelado';
    case 'ASISTIO':
      return 'Asistió';
    case 'AUSENTE':
      return 'No asistió';
    default: {
      const inesperado: never = estado;
      return inesperado;
    }
  }
}

export type EfectoError = 'campos' | 'refrescarHorarios' | 'recargarPrestaciones' | 'consentimiento' | 'ninguno';

export interface AccionError {
  readonly mensaje: string;
  readonly efecto: EfectoError;
}

/** Mensaje fijo del front (nunca el del servidor) y efecto a aplicar en la pantalla ante un error de la API. */
export function accionDeError(error: unknown): AccionError {
  if (error instanceof ErrorConexion) {
    return { mensaje: error.message, efecto: 'ninguno' };
  }
  if (error instanceof ErrorApi) {
    switch (error.codigo) {
      case 'VALIDACION':
        return { mensaje: 'Hay campos con errores. Revisalos e intentá de nuevo.', efecto: 'campos' };
      case 'HORARIO_OCUPADO':
        return { mensaje: 'Ese horario se acaba de ocupar, elegí otro.', efecto: 'refrescarHorarios' };
      case 'HORARIO_NO_DISPONIBLE':
        return { mensaje: 'Ese horario ya no está disponible. Elegí otro.', efecto: 'refrescarHorarios' };
      case 'CONSENTIMIENTO_REQUERIDO':
        return { mensaje: 'Tenés que aceptar el tratamiento de tus datos para reservar.', efecto: 'consentimiento' };
      case 'PRESTACION_INACTIVA':
      case 'RECURSO_NO_ENCONTRADO':
        return { mensaje: 'La prestación elegida ya no está disponible. Elegí otra.', efecto: 'recargarPrestaciones' };
      default:
        break;
    }
  }
  return { mensaje: MENSAJE_GENERICO, efecto: 'ninguno' };
}
