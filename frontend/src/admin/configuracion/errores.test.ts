import { describe, expect, it } from 'vitest';
import { ErrorApi, ErrorConexion } from '../../api/cliente';
import type { ApiError } from '../../api/types';
import { ejemplo } from './ejemplos.testutil';
import { detallesPorCampo, mensajeDeError } from './errores';

const eliminar422 = ejemplo('prestaciones-eliminar.422-prestacion-con-turnos');
const solapados422 = ejemplo('horarios-crear.422-horarios-solapados');
const rango422 = ejemplo('horarios-crear.422-rango-horario-invalido');
const validacion400 = ejemplo('error.400-validacion');
const noAuth401 = ejemplo('error.401-no-autenticado');

function aErrorApi(cuerpo: unknown): ErrorApi {
  const c = cuerpo as ApiError;
  return new ErrorApi(c.status, c.mensaje, c.codigo, c.detalles, c);
}

describe('mensajeDeError', () => {
  it('PRESTACION_CON_TURNOS sugiere desactivar', () => {
    expect(mensajeDeError(aErrorApi(eliminar422))).toMatch(/turnos/i);
    expect(mensajeDeError(aErrorApi(eliminar422))).toMatch(/desactiv/i);
  });

  it('HORARIOS_SOLAPADOS y RANGO_HORARIO_INVALIDO tienen mensajes propios distintos', () => {
    const solapado = mensajeDeError(aErrorApi(solapados422));
    const invalido = mensajeDeError(aErrorApi(rango422));
    expect(solapado).toMatch(/superpone|solap/i);
    expect(invalido).toMatch(/anterior/i);
    expect(solapado).not.toBe(invalido);
  });

  it('VALIDACION y NO_AUTENTICADO tienen mensaje', () => {
    expect(mensajeDeError(aErrorApi(validacion400))).toMatch(/campos/i);
    expect(mensajeDeError(aErrorApi(noAuth401))).toMatch(/sesión/i);
  });

  it('ErrorConexion usa su propio mensaje', () => {
    const e = new ErrorConexion();
    expect(mensajeDeError(e)).toBe(e.message);
  });

  it('errores desconocidos o sin codigo dan mensaje generico sin detalles internos', () => {
    expect(mensajeDeError(new Error('NullPointerException at x.y'))).not.toMatch(/NullPointer/);
    expect(mensajeDeError(new ErrorApi(502, 'x', null))).toMatch(/no se pudo/i);
  });
});

describe('detallesPorCampo', () => {
  it('mapea detalles de VALIDACION por campo', () => {
    expect(detallesPorCampo(aErrorApi(validacion400))).toEqual({
      dni: 'El DNI debe tener 7 u 8 dígitos.',
      email: 'El email es obligatorio.',
    });
  });

  it('devuelve vacio si no es VALIDACION o no es ErrorApi', () => {
    expect(detallesPorCampo(aErrorApi(eliminar422))).toEqual({});
    expect(detallesPorCampo(new Error('x'))).toEqual({});
  });
});
