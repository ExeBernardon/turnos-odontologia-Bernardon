import { ErrorApi, ErrorConexion, MENSAJE_GENERICO } from '../../api/cliente';
import type { Paciente, Turno, TurnoManualRequest } from '../../api/types';
import { formatearHora, ZONA_AGENDA } from '../agenda/agenda.logica';
import { mensajeDeError } from '../configuracion/errores';
import { armarRequest, type EntradaPaciente } from '../pacientes.logica';

/** Acciones que la agenda ofrece sobre un turno y su disponibilidad en este momento. */
export interface AccionesPermitidas {
  readonly confirmar: boolean;
  readonly reprogramar: boolean;
  readonly cancelar: boolean;
  readonly asistencia: {
    readonly visible: boolean;
    readonly habilitada: boolean;
    /** Hora `HH:mm` de Buenos Aires desde la que se puede marcar la asistencia. */
    readonly desde: string;
  };
}

/**
 * Que botones corresponden a un turno. El reloj del navegador solo habilita o deshabilita:
 * la autoridad es el backend (un 422 se informa y se recarga la agenda).
 */
export function accionesPermitidas(turno: Turno, ahora: Date): AccionesPermitidas {
  const activo = turno.estado === 'PENDIENTE' || turno.estado === 'CONFIRMADO';
  const inicio = Date.parse(turno.inicio);
  const empezado = inicio <= ahora.getTime();
  return {
    confirmar: turno.estado === 'PENDIENTE' && !empezado,
    reprogramar: activo,
    cancelar: activo,
    asistencia: { visible: activo, habilitada: activo && empezado, desde: formatearHora(turno.inicio) },
  };
}

// ------------------------------------------------------------------ Alta manual

const EMAIL_MAX = 150;
const TELEFONO = /^[0-9+()\-\s]{6,30}$/;

export interface ErroresContacto {
  email?: string;
  telefono?: string;
}

/** Valida el contacto segun el contrato (`emailContacto` hasta 150 caracteres; `telefonoContacto` 6 a 30). */
export function validarContacto(email: string, telefono: string): ErroresContacto {
  const errores: ErroresContacto = {};
  const correo = email.trim();
  if (correo === '') {
    errores.email = 'El email es obligatorio.';
  } else if (!correo.includes('@') || correo.startsWith('@') || correo.endsWith('@')) {
    errores.email = 'Ingresá un email válido (ej. nombre@ejemplo.com).';
  } else if (correo.length > EMAIL_MAX) {
    errores.email = `El email no puede superar los ${EMAIL_MAX} caracteres.`;
  }
  const numero = telefono.trim();
  if (numero === '') {
    errores.telefono = 'El teléfono es obligatorio.';
  } else if (!TELEFONO.test(numero)) {
    errores.telefono = 'El teléfono debe tener entre 6 y 30 caracteres: números, +, (), - y espacios.';
  }
  return errores;
}

export type PacienteElegido =
  | { readonly tipo: 'existente'; readonly paciente: Paciente }
  | { readonly tipo: 'nuevo'; readonly datos: EntradaPaciente };

export interface EntradaTurnoManual {
  readonly paciente: PacienteElegido;
  readonly prestacionId: number;
  readonly inicio: string;
  readonly emailContacto: string;
  readonly telefonoContacto: string;
}

/** Arma el body del contrato: exactamente uno de `pacienteId` o `paciente` (oneOf). */
export function armarTurnoManual(entrada: EntradaTurnoManual): TurnoManualRequest {
  const comunes = {
    prestacionId: entrada.prestacionId,
    inicio: entrada.inicio,
    emailContacto: entrada.emailContacto.trim(),
    telefonoContacto: entrada.telefonoContacto.trim(),
  };
  if (entrada.paciente.tipo === 'existente') {
    return { pacienteId: entrada.paciente.paciente.id, ...comunes };
  }
  return { paciente: armarRequest(entrada.paciente.datos), ...comunes };
}

// ------------------------------------------------------------------ Errores

export type ClaveError = 'alta' | 'confirmar' | 'cancelar' | 'estado' | 'reprogramar';

export type EfectoError =
  | 'refrescarHorarios'
  | 'recargarPrestaciones'
  | 'campoDni'
  | 'campos'
  | 'recargarAgenda'
  | 'ninguno';

export interface AccionDeError {
  /** Vacio cuando no hay nada para decir (401: lo maneja la expiracion de sesion). */
  readonly mensaje: string;
  readonly efecto: EfectoError;
}

const TRANSICION: Record<ClaveError, string> = {
  alta: 'No se pudo cargar el turno.',
  confirmar: 'El turno ya no se puede confirmar. Actualizamos la agenda.',
  cancelar: 'El turno ya no se puede cancelar. Actualizamos la agenda.',
  estado: 'Solo se puede marcar la asistencia desde la hora de inicio del turno.',
  reprogramar: 'El turno ya no se puede reprogramar. Actualizamos la agenda.',
};

/** Traduce un error de la API a un mensaje propio y a lo que hay que hacer; nunca muestra el `mensaje` del servidor. */
export function accionDeErrorTurno(error: unknown, accion: ClaveError): AccionDeError {
  if (error instanceof ErrorConexion) {
    return { mensaje: error.message, efecto: 'ninguno' };
  }
  if (error instanceof ErrorApi) {
    if (error.status === 401) {
      return { mensaje: '', efecto: 'ninguno' };
    }
    switch (error.codigo) {
      case 'HORARIO_OCUPADO':
      case 'HORARIO_NO_DISPONIBLE':
        return {
          mensaje: 'Ese horario ya no está disponible. Actualizamos los horarios libres: elegí otro.',
          efecto: 'refrescarHorarios',
        };
      case 'PRESTACION_INACTIVA':
        return {
          mensaje: 'La prestación ya no está activa. Actualizamos la lista: elegí otra.',
          efecto: 'recargarPrestaciones',
        };
      case 'DNI_DUPLICADO':
        return {
          mensaje: 'Ya existe un paciente con ese DNI. Buscalo en "Paciente existente".',
          efecto: 'campoDni',
        };
      case 'VALIDACION':
        return { mensaje: mensajeDeError(error), efecto: 'campos' };
      case 'TRANSICION_INVALIDA':
        return { mensaje: TRANSICION[accion], efecto: 'recargarAgenda' };
      case 'RECURSO_NO_ENCONTRADO':
        return { mensaje: 'El turno ya no existe. Actualizamos la agenda.', efecto: 'recargarAgenda' };
      default:
        break;
    }
  }
  return { mensaje: MENSAJE_GENERICO, efecto: 'ninguno' };
}

// ------------------------------------------------------------------ Resultado

export type AccionTurno = 'alta' | 'confirmar' | 'cancelar' | 'reprogramar' | 'asistio' | 'ausente';

const DIA_LARGO = new Intl.DateTimeFormat('es-AR', {
  timeZone: ZONA_AGENDA,
  weekday: 'long',
  day: 'numeric',
  month: 'long',
});

/** Ej.: `martes 1 de diciembre, 18:00`, siempre en hora de Buenos Aires. */
export function describirHorario(instante: string): string {
  return `${diaLargo(instante)}, ${formatearHora(instante)}`;
}

function diaLargo(instante: string): string {
  const partes: Record<string, string> = {};
  for (const parte of DIA_LARGO.formatToParts(new Date(instante))) {
    partes[parte.type] = parte.value;
  }
  return `${partes['weekday'] ?? ''} ${partes['day'] ?? ''} de ${partes['month'] ?? ''}`;
}

function estadoDe(turno: Turno): string {
  return turno.estado === 'CONFIRMADO' ? 'confirmado' : 'pendiente de confirmación';
}

/** Anuncio del resultado de una accion; el estado sale de la respuesta del backend, no de una prediccion. */
export function textoResultado(turno: Turno, accion: AccionTurno): string {
  switch (accion) {
    case 'alta':
      return `Turno cargado: ${estadoDe(turno)}`;
    case 'reprogramar': {
      const estado = turno.estado === 'CONFIRMADO' ? 'queda confirmado' : 'queda pendiente de confirmación';
      return `Turno reprogramado al ${describirHorario(turno.inicio)}: ${estado}`;
    }
    case 'confirmar':
      return 'Turno confirmado';
    case 'cancelar':
      return 'Turno cancelado';
    case 'asistio':
      return 'Asistencia registrada';
    case 'ausente':
      return 'Ausencia registrada';
  }
}
