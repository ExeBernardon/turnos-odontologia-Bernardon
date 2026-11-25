import { describe, expect, it } from 'vitest';
import { ErrorApi, ErrorConexion, MENSAJE_GENERICO } from '../api/cliente';
import type { ApiError, ReservaPublicaRequest } from '../api/types';
import { ejemplo } from '../admin/configuracion/ejemplos.testutil';
import {
  type EntradaReserva,
  accionDeError,
  armarReserva,
  formatearFechaTurno,
  formatearHora,
  textoEstado,
  validarFecha,
  validarReserva,
  ventanaFechas,
} from './reservar.logica';

const REQUEST = ejemplo('turnos-publico-reservar.request') as ReservaPublicaRequest;

const ENTRADA_VALIDA: EntradaReserva = {
  nombre: REQUEST.nombre,
  apellido: REQUEST.apellido,
  dni: REQUEST.dni,
  email: REQUEST.email,
  telefono: REQUEST.telefono,
  consentimiento: true,
};

function conCampo(campo: keyof EntradaReserva, valor: string | boolean): EntradaReserva {
  return { ...ENTRADA_VALIDA, [campo]: valor };
}

describe('ventanaFechas', () => {
  it('va de hoy (BA) a hoy + 60 dias', () => {
    expect(ventanaFechas(new Date('2026-11-30T15:00:00Z'))).toEqual({ min: '2026-11-30', max: '2027-01-29' });
  });

  it('a las 23:30 de Buenos Aires (ya es el dia siguiente en UTC) el minimo sigue siendo hoy', () => {
    expect(ventanaFechas(new Date('2026-12-01T02:30:00Z'))).toEqual({ min: '2026-11-30', max: '2027-01-29' });
  });
});

describe('validarFecha', () => {
  const ventana = { min: '2026-11-30', max: '2027-01-29' };

  it.each([
    ['vacia', ''],
    ['mal formada', '2026-13-01'],
    ['texto', 'manana'],
    ['antes del minimo', '2026-11-29'],
    ['despues del maximo', '2027-01-30'],
  ])('rechaza una fecha %s con un mensaje', (_caso, fecha) => {
    expect(validarFecha(fecha, ventana)).toEqual(expect.any(String));
  });

  it.each(['2026-11-30', '2027-01-29', '2026-12-15'])('acepta %s', (fecha) => {
    expect(validarFecha(fecha, ventana)).toBeUndefined();
  });
});

describe('formatearHora', () => {
  it('muestra HH:mm de Buenos Aires para un instante con offset', () => {
    expect(formatearHora('2026-12-01T09:30:00-03:00')).toBe('09:30');
  });

  it('convierte un instante en Z a hora de Buenos Aires', () => {
    expect(formatearHora('2026-12-01T12:30:00Z')).toBe('09:30');
  });

  it('devuelve cadena vacia ante un instante invalido', () => {
    expect(formatearHora('no es una fecha')).toBe('');
  });
});

describe('formatearFechaTurno', () => {
  it('escribe el dia en palabras', () => {
    expect(formatearFechaTurno('2026-12-10T10:00:00-03:00')).toBe('jueves 10 de diciembre de 2026');
  });

  it('un instante Z cerca de medianoche cae en el dia de Buenos Aires', () => {
    expect(formatearFechaTurno('2026-12-11T02:30:00Z')).toBe('jueves 10 de diciembre de 2026');
    expect(formatearFechaTurno('2026-12-11T03:00:00Z')).toBe('viernes 11 de diciembre de 2026');
  });

  it('devuelve cadena vacia ante un instante invalido', () => {
    expect(formatearFechaTurno('nada')).toBe('');
  });
});

describe('validarReserva', () => {
  it('no marca errores con los datos del ejemplo del contrato', () => {
    expect(validarReserva(ENTRADA_VALIDA)).toEqual({});
  });

  it.each([
    ['nombre', ''],
    ['nombre', 'a'.repeat(101)],
    ['apellido', '   '],
    ['apellido', 'a'.repeat(101)],
    ['dni', '99-999-999'],
    ['dni', '123456'],
    ['dni', '123456789'],
    ['email', 'paciente@'],
    ['email', '@ejemplo.com'],
    ['email', 'a'.repeat(140) + '@ejemplo.com'],
    ['email', ''],
    ['telefono', 'abc'],
    ['telefono', '12345'],
    ['telefono', '1'.repeat(31)],
  ] as const)('marca error en %s con %j', (campo, valor) => {
    const errores = validarReserva(conCampo(campo, valor));
    expect(errores[campo]).toEqual(expect.any(String));
    expect(Object.keys(errores)).toEqual([campo]);
  });

  it('exige el consentimiento', () => {
    const errores = validarReserva(conCampo('consentimiento', false));
    expect(errores.consentimiento).toEqual(expect.any(String));
    expect(Object.keys(errores)).toEqual(['consentimiento']);
  });

  it('acepta telefono de 6 y de 30 caracteres y email de 150', () => {
    expect(validarReserva(conCampo('telefono', '123456'))).toEqual({});
    expect(validarReserva(conCampo('telefono', '1'.repeat(30)))).toEqual({});
    expect(validarReserva(conCampo('email', 'a'.repeat(138) + '@ejemplo.com'))).toEqual({});
  });

  it('informa varios errores a la vez', () => {
    const errores = validarReserva({ ...ENTRADA_VALIDA, dni: '99-999-999', email: 'paciente@', telefono: 'abc' });
    expect(Object.keys(errores).sort()).toEqual(['dni', 'email', 'telefono']);
  });
});

describe('armarReserva', () => {
  it('con los valores del ejemplo devuelve exactamente el request del contrato', () => {
    expect(armarReserva(ENTRADA_VALIDA, REQUEST.prestacionId, REQUEST.inicio)).toEqual(REQUEST);
  });

  it('recorta textos, conserva el DNI como se escribio y no toca el inicio', () => {
    const body = armarReserva(
      { nombre: ' Ana ', apellido: ' Ejemplo ', dni: '99.999.999', email: ' paciente@ejemplo.com ', telefono: ' 1155550000 ', consentimiento: true },
      2,
      '2026-12-11T02:30:00Z',
    );
    expect(body).toEqual({
      nombre: 'Ana',
      apellido: 'Ejemplo',
      dni: '99.999.999',
      email: 'paciente@ejemplo.com',
      telefono: '1155550000',
      prestacionId: 2,
      inicio: '2026-12-11T02:30:00Z',
      consentimiento: true,
    });
  });
});

describe('textoEstado', () => {
  it.each([
    ['PENDIENTE', 'Pendiente de confirmación'],
    ['CONFIRMADO', 'Confirmado'],
    ['CANCELADO', 'Cancelado'],
    ['ASISTIO', 'Asistió'],
    ['AUSENTE', 'No asistió'],
  ] as const)('%s -> %s', (estado, texto) => {
    expect(textoEstado(estado)).toBe(texto);
  });
});

describe('accionDeError', () => {
  function apiError(archivo: string): ErrorApi {
    const c = ejemplo(archivo) as ApiError;
    return new ErrorApi(c.status, c.mensaje, c.codigo, c.detalles, c);
  }

  it.each([
    ['error.400-validacion', 'campos', 'Hay campos con errores. Revisalos e intentá de nuevo.'],
    ['turnos-publico-reservar.409-horario-ocupado', 'refrescarHorarios', 'Ese horario se acaba de ocupar, elegí otro.'],
    ['turnos-publico-reservar.422-horario-no-disponible', 'refrescarHorarios', 'Ese horario ya no está disponible. Elegí otro.'],
    ['turnos-publico-reservar.422-consentimiento-requerido', 'consentimiento', 'Tenés que aceptar el tratamiento de tus datos para reservar.'],
    ['turnos-publico-reservar.422-prestacion-inactiva', 'recargarPrestaciones', 'La prestación elegida ya no está disponible. Elegí otra.'],
    ['error.404-recurso-no-encontrado', 'recargarPrestaciones', 'La prestación elegida ya no está disponible. Elegí otra.'],
    ['error.400-solicitud-mal-formada', 'ninguno', MENSAJE_GENERICO],
    ['error.500-error-interno', 'ninguno', MENSAJE_GENERICO],
  ] as const)('%s -> efecto %s', (archivo, efecto, mensaje) => {
    expect(accionDeError(apiError(archivo))).toEqual({ mensaje, efecto });
  });

  it('nunca muestra el mensaje del servidor', () => {
    const error = apiError('turnos-publico-reservar.409-horario-ocupado');
    expect(accionDeError(error).mensaje).not.toBe(error.message);
  });

  it('un ErrorApi sin codigo, un Error cualquiera y un valor raro dan el mensaje generico', () => {
    expect(accionDeError(new ErrorApi(502, 'Bad Gateway', null))).toEqual({ mensaje: MENSAJE_GENERICO, efecto: 'ninguno' });
    expect(accionDeError(new Error('boom stack'))).toEqual({ mensaje: MENSAJE_GENERICO, efecto: 'ninguno' });
    expect(accionDeError('texto')).toEqual({ mensaje: MENSAJE_GENERICO, efecto: 'ninguno' });
  });

  it('un error de conexion usa su propio mensaje', () => {
    const error = new ErrorConexion();
    expect(accionDeError(error)).toEqual({ mensaje: error.message, efecto: 'ninguno' });
  });
});
