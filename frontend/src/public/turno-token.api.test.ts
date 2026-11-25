import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../api/cliente';
import { ejemplo } from '../admin/configuracion/ejemplos.testutil';
import { respuesta, stubServidor } from '../admin/configuracion/servidor.testutil';
import { cancelarTurnoPorToken, confirmarTurnoPorToken, obtenerTurnoPorToken } from './turno-token.api';

const TOKEN = 'TOKEN-DE-EJEMPLO';
const RUTA = `/api/public/turnos/${TOKEN}`;

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('obtenerTurnoPorToken', () => {
  it('pide GET /api/public/turnos/{token} y parsea el ejemplo pendiente', async () => {
    const { llamadas } = stubServidor({ ['GET ' + RUTA]: () => respuesta(200, ejemplo('turnos-publico-obtener.200-pendiente')) });
    const resumen = await obtenerTurnoPorToken(TOKEN);
    expect(llamadas).toEqual([{ metodo: 'GET', url: RUTA, body: undefined }]);
    expect(resumen).toEqual(ejemplo('turnos-publico-obtener.200-pendiente'));
    expect(resumen.puedeConfirmar).toBe(true);
  });

  it('parsea el ejemplo confirmado', async () => {
    stubServidor({ ['GET ' + RUTA]: () => respuesta(200, ejemplo('turnos-publico-obtener.200-confirmado')) });
    const resumen = await obtenerTurnoPorToken(TOKEN);
    expect(resumen.estado).toBe('CONFIRMADO');
    expect(resumen.puedeCancelar).toBe(false);
  });

  it('descarta campos extra como dni, email y telefono', async () => {
    const conExtras = { ...(ejemplo('turnos-publico-obtener.200-pendiente') as object), dni: '99999999', email: 'ana@ejemplo.com', telefono: '1100000000' };
    stubServidor({ ['GET ' + RUTA]: () => respuesta(200, conExtras) });
    const resumen = await obtenerTurnoPorToken(TOKEN);
    expect(Object.keys(resumen)).not.toContain('dni');
    expect(Object.keys(resumen)).not.toContain('email');
    expect(Object.keys(resumen)).not.toContain('telefono');
  });

  it('rechaza una respuesta sin puedeConfirmar', async () => {
    const sinFlag = { ...(ejemplo('turnos-publico-obtener.200-pendiente') as Record<string, unknown>) };
    delete sinFlag['puedeConfirmar'];
    stubServidor({ ['GET ' + RUTA]: () => respuesta(200, sinFlag) });
    await expect(obtenerTurnoPorToken(TOKEN)).rejects.toThrow();
  });

  it('propaga el 404 del contrato como ErrorApi TOKEN_INVALIDO', async () => {
    stubServidor({ ['GET ' + RUTA]: () => respuesta(404, ejemplo('turnos-publico-obtener.404-token-invalido')) });
    await expect(obtenerTurnoPorToken(TOKEN)).rejects.toMatchObject({ status: 404, codigo: 'TOKEN_INVALIDO' });
  });
});

describe.each([
  ['confirmar', confirmarTurnoPorToken, 'turnos-publico-confirmar', 'CONFIRMADO', 'CONFIRMACION_FUERA_DE_PLAZO', '422-confirmacion-fuera-de-plazo'],
  ['cancelar', cancelarTurnoPorToken, 'turnos-publico-cancelar', 'CANCELADO', 'CANCELACION_FUERA_DE_PLAZO', '422-cancelacion-fuera-de-plazo'],
] as const)('%s por token', (accion, llamar, prefijo, estadoFinal, codigoPlazo, archivoPlazo) => {
  const POST = `POST ${RUTA}/${accion}`;

  it(`hace POST .../${accion} sin body y parsea el 200 del contrato`, async () => {
    const { llamadas } = stubServidor({ [POST]: () => respuesta(200, ejemplo(`${prefijo}.200`)) });
    const resumen = await llamar(TOKEN);
    expect(llamadas).toEqual([{ metodo: 'POST', url: `${RUTA}/${accion}`, body: undefined }]);
    expect(resumen.estado).toBe(estadoFinal);
  });

  it('propaga el 404 del contrato como ErrorApi', async () => {
    stubServidor({ [POST]: () => respuesta(404, ejemplo(`${prefijo}.404-token-invalido`)) });
    await expect(llamar(TOKEN)).rejects.toMatchObject({ status: 404, codigo: 'TOKEN_INVALIDO' });
  });

  it('propaga el 422 fuera de plazo y el de transicion invalida con su codigo', async () => {
    stubServidor({ [POST]: [() => respuesta(422, ejemplo(`${prefijo}.${archivoPlazo}`)), () => respuesta(422, ejemplo(`${prefijo}.422-transicion-invalida`))] });
    const primero = await llamar(TOKEN).catch((e: unknown) => e);
    const segundo = await llamar(TOKEN).catch((e: unknown) => e);
    expect(primero).toBeInstanceOf(ErrorApi);
    expect(primero).toMatchObject({ status: 422, codigo: codigoPlazo });
    expect(segundo).toMatchObject({ status: 422, codigo: 'TRANSICION_INVALIDA' });
  });
});

describe('codificacion del token', () => {
  it.each(['a/b?c=d..e-0123456789', '../../admin/x-0123456789'])('%s viaja con encodeURIComponent', async (token) => {
    const esperada = `/api/public/turnos/${encodeURIComponent(token)}`;
    const { llamadas } = stubServidor({
      ['GET ' + esperada]: () => respuesta(200, ejemplo('turnos-publico-obtener.200-pendiente')),
      ['POST ' + esperada + '/cancelar']: () => respuesta(200, ejemplo('turnos-publico-cancelar.200')),
    });
    await obtenerTurnoPorToken(token);
    await cancelarTurnoPorToken(token);
    expect(llamadas.map((l) => l.url)).toEqual([esperada, esperada + '/cancelar']);
    expect(esperada).not.toMatch(/[?]|\.\.\//);
  });
});
