import { describe, expect, it } from 'vitest';
import type { EstadoTurno, TurnoResumenPublico } from '../api/types';
import { ErrorApi, ErrorConexion, MENSAJE_GENERICO } from '../api/cliente';
import type { ApiError } from '../api/types';
import { ejemplo } from '../admin/configuracion/ejemplos.testutil';
import { accionDeErrorToken, leerToken, vistaDeTurno } from './turno-token.logica';

const INICIO = '2026-12-03T10:00:00-03:00';
const ANTES = new Date('2026-12-02T10:00:00-03:00');
const DESPUES = new Date('2026-12-03T10:00:01-03:00');
const MISMO_INSTANTE = new Date('2026-12-03T10:00:00-03:00');

function turno(estado: EstadoTurno, flags: { confirmar?: boolean; cancelar?: boolean } = {}): TurnoResumenPublico {
  return {
    prestacion: { nombre: 'Consulta', duracionMinutos: 30 },
    inicio: INICIO,
    fin: '2026-12-03T10:30:00-03:00',
    estado,
    nombrePaciente: 'Ana',
    puedeConfirmar: flags.confirmar ?? false,
    puedeCancelar: flags.cancelar ?? false,
    contactoConsultorio: 'Consultorio de ejemplo - Tel. +54 9 11 0000-0000',
  };
}

const YA_PASO = { tipo: 'informativa', mensaje: 'Este turno ya pasó.', contacto: false, reservarOtro: false };

describe('leerToken', () => {
  it('devuelve null sin parametro token', () => {
    expect(leerToken(new URLSearchParams(''))).toBeNull();
    expect(leerToken(new URLSearchParams('otro=TOKEN-DE-EJEMPLO'))).toBeNull();
  });

  it('devuelve null si esta vacio o son solo espacios', () => {
    expect(leerToken(new URLSearchParams('token='))).toBeNull();
    expect(leerToken(new URLSearchParams({ token: '                    ' }))).toBeNull();
  });

  it('devuelve null con 15 caracteres y con 129', () => {
    expect(leerToken(new URLSearchParams({ token: 'a'.repeat(15) }))).toBeNull();
    expect(leerToken(new URLSearchParams({ token: 'a'.repeat(129) }))).toBeNull();
  });

  it('acepta 16 y 128 caracteres', () => {
    expect(leerToken(new URLSearchParams({ token: 'TOKEN-DE-EJEMPLO' }))).toBe('TOKEN-DE-EJEMPLO');
    expect(leerToken(new URLSearchParams({ token: 'b'.repeat(128) }))).toBe('b'.repeat(128));
  });

  it('recorta los espacios alrededor antes de medir', () => {
    expect(leerToken(new URLSearchParams({ token: '  TOKEN-DE-EJEMPLO ' }))).toBe('TOKEN-DE-EJEMPLO');
    expect(leerToken(new URLSearchParams({ token: ' ' + 'a'.repeat(15) + ' ' }))).toBeNull();
  });
});

describe('vistaDeTurno - confirmar', () => {
  it('PENDIENTE con puedeConfirmar es accionable', () => {
    expect(vistaDeTurno(turno('PENDIENTE', { confirmar: true }), 'confirmar', ANTES)).toEqual({ tipo: 'accionable' });
  });

  it('el flag del backend manda: PENDIENTE futuro sin flag no es accionable', () => {
    expect(vistaDeTurno(turno('PENDIENTE', { cancelar: true }), 'confirmar', ANTES)).toEqual({
      tipo: 'informativa',
      mensaje: 'Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes, con el link del recordatorio.',
      contacto: false,
      reservarOtro: false,
    });
  });

  it('CONFIRMADO futuro es idempotente', () => {
    expect(vistaDeTurno(turno('CONFIRMADO'), 'confirmar', ANTES)).toEqual({
      tipo: 'informativa',
      mensaje: 'Tu turno ya estaba confirmado.',
      contacto: false,
      reservarOtro: false,
    });
  });

  it('CANCELADO ofrece reservar otro turno', () => {
    expect(vistaDeTurno(turno('CANCELADO'), 'confirmar', ANTES)).toEqual({
      tipo: 'informativa',
      mensaje: 'Este turno está cancelado y no se puede confirmar.',
      contacto: false,
      reservarOtro: true,
    });
  });

  it.each(['ASISTIO', 'AUSENTE'] as const)('%s ya paso aunque el inicio sea futuro', (estado) => {
    expect(vistaDeTurno(turno(estado), 'confirmar', ANTES)).toEqual(YA_PASO);
  });

  it.each(['PENDIENTE', 'CONFIRMADO'] as const)('%s con inicio posterior al reloj ya paso', (estado) => {
    expect(vistaDeTurno(turno(estado), 'confirmar', DESPUES)).toEqual(YA_PASO);
  });

  it('el borde inicio == ahora ya paso', () => {
    expect(vistaDeTurno(turno('PENDIENTE'), 'confirmar', MISMO_INSTANTE)).toEqual(YA_PASO);
    expect(vistaDeTurno(turno('CONFIRMADO'), 'confirmar', MISMO_INSTANTE)).toEqual(YA_PASO);
  });
});

describe('vistaDeTurno - cancelar', () => {
  const CONTACTO = true;

  it('PENDIENTE con puedeCancelar es accionable', () => {
    expect(vistaDeTurno(turno('PENDIENTE', { cancelar: true }), 'cancelar', ANTES)).toEqual({ tipo: 'accionable' });
  });

  it('el flag del backend manda: PENDIENTE futuro con flag false nunca es accionable', () => {
    expect(vistaDeTurno(turno('PENDIENTE', { confirmar: true }), 'cancelar', ANTES)).toEqual({
      tipo: 'informativa',
      mensaje: 'Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio.',
      contacto: CONTACTO,
      reservarOtro: false,
    });
  });

  it('CANCELADO es idempotente y ofrece reservar otro turno', () => {
    expect(vistaDeTurno(turno('CANCELADO'), 'cancelar', ANTES)).toEqual({
      tipo: 'informativa',
      mensaje: 'Tu turno ya estaba cancelado.',
      contacto: false,
      reservarOtro: true,
    });
  });

  it('CONFIRMADO futuro pide comunicarse con el consultorio', () => {
    expect(vistaDeTurno(turno('CONFIRMADO'), 'cancelar', ANTES)).toEqual({
      tipo: 'informativa',
      mensaje: 'Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio.',
      contacto: CONTACTO,
      reservarOtro: false,
    });
  });

  it.each(['ASISTIO', 'AUSENTE'] as const)('%s ya paso', (estado) => {
    expect(vistaDeTurno(turno(estado), 'cancelar', ANTES)).toEqual(YA_PASO);
  });

  it.each(['PENDIENTE', 'CONFIRMADO'] as const)('%s con inicio <= ahora ya paso (incluido el borde)', (estado) => {
    expect(vistaDeTurno(turno(estado), 'cancelar', DESPUES)).toEqual(YA_PASO);
    expect(vistaDeTurno(turno(estado), 'cancelar', MISMO_INSTANTE)).toEqual(YA_PASO);
  });
});

describe('accionDeErrorToken', () => {
  function apiError(archivo: string): ErrorApi {
    const c = ejemplo(archivo) as ApiError;
    return new ErrorApi(c.status, c.mensaje, c.codigo, c.detalles, c);
  }

  const LINK_INVALIDO = { mensaje: 'El link no es válido.', efecto: 'linkInvalido' };

  it.each([
    'turnos-publico-obtener.404-token-invalido',
    'turnos-publico-confirmar.404-token-invalido',
    'turnos-publico-cancelar.404-token-invalido',
  ])('404 TOKEN_INVALIDO (%s) es link invalido', (archivo) => {
    expect(accionDeErrorToken(apiError(archivo), 'confirmar')).toEqual(LINK_INVALIDO);
  });

  it('400 VALIDACION es link invalido', () => {
    expect(accionDeErrorToken(new ErrorApi(400, 'x', 'VALIDACION'), 'cancelar')).toEqual(LINK_INVALIDO);
  });

  it('422 CONFIRMACION_FUERA_DE_PLAZO muestra el texto fijo y relee', () => {
    expect(accionDeErrorToken(apiError('turnos-publico-confirmar.422-confirmacion-fuera-de-plazo'), 'confirmar')).toEqual({
      mensaje: 'Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes, con el link del recordatorio.',
      efecto: 'releer',
    });
  });

  it('422 CANCELACION_FUERA_DE_PLAZO muestra el texto fijo y relee', () => {
    expect(accionDeErrorToken(apiError('turnos-publico-cancelar.422-cancelacion-fuera-de-plazo'), 'cancelar')).toEqual({
      mensaje: 'Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio.',
      efecto: 'releer',
    });
  });

  it('422 TRANSICION_INVALIDA depende de la accion y relee', () => {
    expect(accionDeErrorToken(apiError('turnos-publico-cancelar.422-transicion-invalida'), 'cancelar')).toEqual({
      mensaje: 'El turno cambió y ya no se puede cancelar desde el link.',
      efecto: 'releer',
    });
    expect(accionDeErrorToken(apiError('turnos-publico-confirmar.422-transicion-invalida'), 'confirmar')).toEqual({
      mensaje: 'El turno cambió y ya no se puede confirmar desde el link.',
      efecto: 'releer',
    });
  });

  it('un error de conexion usa su mensaje y permite reintentar', () => {
    const error = new ErrorConexion();
    expect(accionDeErrorToken(error, 'confirmar')).toEqual({ mensaje: error.message, efecto: 'reintentar' });
  });

  it('500 ERROR_INTERNO, ErrorApi sin codigo y errores cualquiera dan el mensaje generico', () => {
    const generico = { mensaje: MENSAJE_GENERICO, efecto: 'reintentar' };
    expect(accionDeErrorToken(new ErrorApi(500, 'detalle interno', 'ERROR_INTERNO'), 'confirmar')).toEqual(generico);
    expect(accionDeErrorToken(new ErrorApi(502, 'x', null), 'cancelar')).toEqual(generico);
    expect(accionDeErrorToken(new Error('Respuesta inválida: turno'), 'cancelar')).toEqual(generico);
  });

  it('nunca muestra el mensaje del servidor', () => {
    const error = new ErrorApi(500, 'SECRETO-DEL-SERVIDOR', 'ERROR_INTERNO');
    expect(accionDeErrorToken(error, 'confirmar').mensaje).not.toContain('SECRETO');
  });
});
