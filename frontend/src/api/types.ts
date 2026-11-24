/**
 * Tipos del contrato de la API (espejo de `components/schemas` de docs/api/openapi.yaml).
 * Escritos a mano, sin `any`. Si el contrato cambia, se actualizan openapi.yaml,
 * errores.md, tipos.ts y ejemplos/ juntos en el mismo change (AGENTS.md regla 5).
 * C-02 copia este archivo a frontend/src/api/types.ts.
 */

// ---------------------------------------------------------------- Primitivos

/** ISO-8601 con offset obligatorio, ej. "2026-12-01T10:00:00-03:00". */
export type Instante = string;
/** Día calendario YYYY-MM-DD (zona de Buenos Aires). */
export type FechaDia = string;
/** Hora local HH:mm. */
export type HoraDelDia = string;
/** DNI devuelto siempre solo con dígitos (7 u 8). */
export type Dni = string;
/** DNI aceptado con o sin puntos (ej. "99.999.999"). */
export type DniEntrada = string;
export type Email = string;
export type Telefono = string;
export type NombrePersona = string;
/** Ids enteros (int64 en el backend). */
export type Id = number;

// ---------------------------------------------------------------- Enums

export type EstadoTurno = 'PENDIENTE' | 'CONFIRMADO' | 'CANCELADO' | 'ASISTIO' | 'AUSENTE';
export type CanceladoPor = 'PACIENTE' | 'ODONTOLOGO';

export type CodigoError =
  | 'VALIDACION'
  | 'SOLICITUD_MAL_FORMADA'
  | 'NO_AUTENTICADO'
  | 'CREDENCIALES_INVALIDAS'
  | 'RECURSO_NO_ENCONTRADO'
  | 'TOKEN_INVALIDO'
  | 'HORARIO_OCUPADO'
  | 'BLOQUEO_CON_TURNOS_AFECTADOS'
  | 'HORARIO_NO_DISPONIBLE'
  | 'CONSENTIMIENTO_REQUERIDO'
  | 'CANCELACION_FUERA_DE_PLAZO'
  | 'CONFIRMACION_FUERA_DE_PLAZO'
  | 'TRANSICION_INVALIDA'
  | 'PRESTACION_INACTIVA'
  | 'PRESTACION_CON_TURNOS'
  | 'DNI_DUPLICADO'
  | 'RANGO_HORARIO_INVALIDO'
  | 'HORARIOS_SOLAPADOS'
  | 'ERROR_INTERNO';

// ---------------------------------------------------------------- Errores

export interface DetalleError {
  readonly campo: string;
  readonly mensaje: string;
}

/** Schema `Error` del OpenAPI. `detalles` solo aparece en 400 VALIDACION. */
export interface ApiError {
  readonly timestamp: Instante;
  readonly status: 400 | 401 | 404 | 409 | 422 | 500;
  readonly error: string;
  readonly codigo: CodigoError;
  readonly mensaje: string;
  readonly path: string;
  readonly detalles?: readonly DetalleError[];
}

/** 409 BLOQUEO_CON_TURNOS_AFECTADOS (schema `ErrorBloqueoConflicto`). */
export interface ErrorBloqueoConflicto extends ApiError {
  readonly turnosAfectados: readonly Turno[];
}

// ---------------------------------------------------------------- Auth

export interface LoginRequest {
  email: Email;
  password: string;
}

export interface LoginResponse {
  readonly token: string;
  readonly tipo: 'Bearer';
  readonly expiraEn: Instante;
  readonly nombre: string;
}

export interface Usuario {
  readonly id: Id;
  readonly nombre: string;
  readonly email: Email;
}

// ---------------------------------------------------------------- Prestaciones

export interface PrestacionPublica {
  readonly id: Id;
  readonly nombre: string;
  readonly duracionMinutos: number;
}

export interface Prestacion {
  readonly id: Id;
  readonly nombre: string;
  readonly duracionMinutos: number;
  readonly activa: boolean;
}

/** `duracionMinutos` 1..480; `activa` por defecto true. PUT reemplaza completo. */
export interface PrestacionRequest {
  nombre: string;
  duracionMinutos: number;
  activa?: boolean;
}

export interface PrestacionRef {
  readonly id: Id;
  readonly nombre: string;
  readonly duracionMinutos: number;
}

export interface PrestacionResumen {
  readonly nombre: string;
  readonly duracionMinutos: number;
}

// ---------------------------------------------------------------- Horarios de atención

export interface HorarioAtencion {
  readonly id: Id;
  /** ISO-8601: 1 = lunes ... 7 = domingo. */
  readonly diaSemana: number;
  readonly horaInicio: HoraDelDia;
  readonly horaFin: HoraDelDia;
}

export interface HorarioAtencionRequest {
  diaSemana: number;
  horaInicio: HoraDelDia;
  horaFin: HoraDelDia;
}

// ---------------------------------------------------------------- Bloqueos

export interface Bloqueo {
  readonly id: Id;
  readonly inicio: Instante;
  readonly fin: Instante;
  readonly motivo: string | null;
  readonly creadoEn: Instante;
}

export interface BloqueoRequest {
  inicio: Instante;
  fin: Instante;
  motivo?: string;
}

export interface BloqueoCrearRequest extends BloqueoRequest {
  /** Por defecto false: con turnos afectados la API responde 409. */
  cancelarTurnosAfectados?: boolean;
}

export interface BloqueoPrevisualizacion {
  readonly turnosAfectados: readonly Turno[];
}

export interface BloqueoCreado {
  readonly bloqueo: Bloqueo;
  readonly turnosCancelados: readonly Turno[];
}

// ---------------------------------------------------------------- Pacientes

export interface Contacto {
  readonly email: Email;
  readonly telefono: Telefono;
}

export interface Paciente {
  readonly id: Id;
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: Dni;
  /** null si el paciente no tiene turnos. */
  readonly ultimoContacto: Contacto | null;
}

export interface PacienteRequest {
  nombre: NombrePersona;
  apellido: NombrePersona;
  dni: DniEntrada;
}

export interface PacienteRef {
  readonly id: Id;
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: Dni;
}

// ---------------------------------------------------------------- Turnos (odontólogo)

/** Turno de la agenda. Nunca incluye el token. */
export interface Turno {
  readonly id: Id;
  readonly inicio: Instante;
  readonly fin: Instante;
  readonly estado: EstadoTurno;
  readonly canceladoPor: CanceladoPor | null;
  readonly paciente: PacienteRef;
  readonly prestacion: PrestacionRef;
  readonly emailContacto: Email;
  readonly telefonoContacto: Telefono;
  readonly consentimientoAt: Instante | null;
  /** true si está PENDIENTE y empieza en menos de 24 h. */
  readonly pendienteMenos24h: boolean;
  readonly creadoEn: Instante;
}

interface TurnoManualBase {
  prestacionId: Id;
  inicio: Instante;
  emailContacto: Email;
  telefonoContacto: Telefono;
}

/** Alta manual: exactamente uno de `pacienteId` o `paciente` (oneOf). */
export type TurnoManualRequest =
  | (TurnoManualBase & { pacienteId: Id; paciente?: never })
  | (TurnoManualBase & { paciente: PacienteRequest; pacienteId?: never });

export interface ReprogramarRequest {
  inicio: Instante;
}

export interface EstadoAsistenciaRequest {
  estado: 'ASISTIO' | 'AUSENTE';
}

// ---------------------------------------------------------------- Público

export interface ReservaPublicaRequest {
  nombre: NombrePersona;
  apellido: NombrePersona;
  dni: DniEntrada;
  email: Email;
  telefono: Telefono;
  prestacionId: Id;
  inicio: Instante;
  /** Obligatorio; con false la API responde 422 CONSENTIMIENTO_REQUERIDO. */
  consentimiento: boolean;
}

/**
 * Resumen público por token. Sin DNI, apellido, email, teléfono, ids ni token.
 * Para el paciente confirmar es definitivo: `puedeCancelar` es false si está CONFIRMADO.
 */
export interface TurnoResumenPublico {
  readonly prestacion: PrestacionResumen;
  readonly inicio: Instante;
  readonly fin: Instante;
  readonly estado: EstadoTurno;
  /** Solo nombre de pila. */
  readonly nombrePaciente: string;
  /** PENDIENTE y faltan 48 h o menos (y más de 0). */
  readonly puedeConfirmar: boolean;
  /** PENDIENTE y faltan 24 h o más. */
  readonly puedeCancelar: boolean;
  readonly contactoConsultorio: string;
}

// ---------------------------------------------------------------- Disponibilidad

export interface DiaDisponible {
  readonly fecha: FechaDia;
  /** Vacío si no hay lugar ese día. */
  readonly horarios: readonly Instante[];
}

export interface Disponibilidad {
  readonly prestacion: PrestacionRef;
  /** Un elemento por cada fecha del rango pedido. */
  readonly dias: readonly DiaDisponible[];
}
