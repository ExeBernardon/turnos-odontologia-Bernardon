import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, ErrorConexion, pedir, registrarManejadorExpiracion } from './cliente';
import { borrarToken, guardarToken } from '../shared/sesion';

function respuestaJson(status: number, cuerpo: unknown): Response {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'Content-Type': 'application/json' } });
}

const parsearNombre = (dato: unknown): string => {
  if (typeof dato === 'object' && dato !== null && 'nombre' in dato && typeof dato.nombre === 'string') {
    return dato.nombre;
  }
  throw new Error('respuesta inesperada');
};

describe('cliente HTTP', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    registrarManejadorExpiracion(null);
    borrarToken();
  });

  afterEach(() => {
    registrarManejadorExpiracion(null);
    vi.unstubAllGlobals();
  });

  it('exito: pide a /api relativo, envia JSON y entrega el dato parseado', async () => {
    fetchMock.mockResolvedValue(respuestaJson(201, { nombre: 'Limpieza' }));

    const r = await pedir('POST', '/api/prestaciones', { body: { nombre: 'Limpieza' }, parsear: parsearNombre });

    expect(r).toBe('Limpieza');
    const [url, init] = fetchMock.mock.calls[0] ?? [];
    expect(url).toBe('/api/prestaciones');
    expect(init?.method).toBe('POST');
    expect(init?.body).toBe('{"nombre":"Limpieza"}');
    expect(new Headers(init?.headers).get('Content-Type')).toBe('application/json');
  });

  it('204 sin contenido llega al parseador como undefined', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const r = await pedir('DELETE', '/api/prestaciones/1', { parsear: (d) => d === undefined });
    expect(r).toBe(true);
  });

  it('error uniforme -> ErrorApi tipado con status, codigo, mensaje y detalles', async () => {
    fetchMock.mockResolvedValue(
      respuestaJson(409, {
        timestamp: '2026-11-30T10:15:00-03:00',
        status: 409,
        error: 'Conflict',
        codigo: 'HORARIO_OCUPADO',
        mensaje: 'Ese horario ya fue reservado. Elegí otro.',
        path: '/api/public/turnos',
      }),
    );

    const falla = await pedir('POST', '/api/public/turnos', { parsear: parsearNombre }).catch((e: unknown) => e);

    expect(falla).toBeInstanceOf(ErrorApi);
    const e = falla as ErrorApi;
    expect(e.status).toBe(409);
    expect(e.codigo).toBe('HORARIO_OCUPADO');
    expect(e.message).toBe('Ese horario ya fue reservado. Elegí otro.');
  });

  it('400 VALIDACION conserva los detalles por campo', async () => {
    fetchMock.mockResolvedValue(
      respuestaJson(400, {
        timestamp: '2026-11-30T10:15:00-03:00',
        status: 400,
        error: 'Bad Request',
        codigo: 'VALIDACION',
        mensaje: 'Hay campos con errores.',
        path: '/api/pacientes',
        detalles: [{ campo: 'dni', mensaje: 'Formato inválido' }],
      }),
    );

    const e = (await pedir('POST', '/api/pacientes', { parsear: parsearNombre }).catch((x: unknown) => x)) as ErrorApi;

    expect(e.codigo).toBe('VALIDACION');
    expect(e.detalles).toEqual([{ campo: 'dni', mensaje: 'Formato inválido' }]);
  });

  it('error sin el formato uniforme (HTML del proxy) -> status y mensaje generico', async () => {
    fetchMock.mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 }));

    const e = (await pedir('GET', '/api/turnos', { parsear: parsearNombre }).catch((x: unknown) => x)) as ErrorApi;

    expect(e).toBeInstanceOf(ErrorApi);
    expect(e.status).toBe(502);
    expect(e.codigo).toBeNull();
    expect(e.message).toBe('No se pudo completar la operación. Intentá de nuevo en unos minutos.');
  });

  it('JSON de error con codigo fuera del catalogo tambien es generico', async () => {
    fetchMock.mockResolvedValue(respuestaJson(500, { codigo: 'INVENTADO', mensaje: 'x' }));

    const e = (await pedir('GET', '/api/turnos', { parsear: parsearNombre }).catch((x: unknown) => x)) as ErrorApi;

    expect(e.codigo).toBeNull();
    expect(e.status).toBe(500);
  });

  it('con sesion adjunta Authorization Bearer; sin sesion no', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(respuestaJson(200, { nombre: 'x' })));

    await pedir('GET', '/api/turnos', { parsear: parsearNombre });
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get('Authorization')).toBeNull();

    guardarToken('token-ficticio');
    await pedir('GET', '/api/turnos', { parsear: parsearNombre });
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get('Authorization')).toBe('Bearer token-ficticio');
  });

  it('falla de red -> ErrorConexion, distinguible de ErrorApi', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    const falla = await pedir('GET', '/api/turnos', { parsear: parsearNombre }).catch((e: unknown) => e);

    expect(falla).toBeInstanceOf(ErrorConexion);
    expect(falla).not.toBeInstanceOf(ErrorApi);
  });

  it('401 NO_AUTENTICADO notifica la expiracion y entrega el mismo ErrorApi', async () => {
    const manejador = vi.fn();
    registrarManejadorExpiracion(manejador);
    fetchMock.mockResolvedValue(
      respuestaJson(401, {
        timestamp: '2026-11-30T10:15:00-03:00',
        status: 401,
        error: 'Unauthorized',
        codigo: 'NO_AUTENTICADO',
        mensaje: 'Tu sesión expiró o no es válida. Volvé a iniciar sesión.',
        path: '/api/turnos',
      }),
    );

    const falla = await pedir('GET', '/api/turnos', { parsear: parsearNombre }).catch((e: unknown) => e);

    expect(falla).toBeInstanceOf(ErrorApi);
    expect((falla as ErrorApi).codigo).toBe('NO_AUTENTICADO');
    expect(manejador).toHaveBeenCalledTimes(1);
  });

  it('401 CREDENCIALES_INVALIDAS del login no notifica la expiracion', async () => {
    const manejador = vi.fn();
    registrarManejadorExpiracion(manejador);
    fetchMock.mockResolvedValue(
      respuestaJson(401, {
        timestamp: '2026-11-30T10:15:00-03:00',
        status: 401,
        error: 'Unauthorized',
        codigo: 'CREDENCIALES_INVALIDAS',
        mensaje: 'Email o contraseña incorrectos.',
        path: '/api/auth/login',
      }),
    );

    const falla = await pedir('POST', '/api/auth/login', { parsear: parsearNombre }).catch((e: unknown) => e);

    expect((falla as ErrorApi).codigo).toBe('CREDENCIALES_INVALIDAS');
    expect(manejador).not.toHaveBeenCalled();
  });

  it('un error que no es 401 NO_AUTENTICADO no notifica', async () => {
    const manejador = vi.fn();
    registrarManejadorExpiracion(manejador);
    fetchMock.mockResolvedValue(
      respuestaJson(409, {
        timestamp: '2026-11-30T10:15:00-03:00',
        status: 409,
        error: 'Conflict',
        codigo: 'HORARIO_OCUPADO',
        mensaje: 'Ese horario ya fue reservado. Elegí otro.',
        path: '/api/turnos',
      }),
    );

    await pedir('POST', '/api/turnos', { parsear: parsearNombre }).catch(() => undefined);

    expect(manejador).not.toHaveBeenCalled();
  });

  it('sin handler registrado, un 401 NO_AUTENTICADO solo lanza el ErrorApi', async () => {
    fetchMock.mockResolvedValue(
      respuestaJson(401, {
        timestamp: '2026-11-30T10:15:00-03:00',
        status: 401,
        error: 'Unauthorized',
        codigo: 'NO_AUTENTICADO',
        mensaje: 'Tu sesión expiró o no es válida. Volvé a iniciar sesión.',
        path: '/api/turnos',
      }),
    );

    const falla = await pedir('GET', '/api/turnos', { parsear: parsearNombre }).catch((e: unknown) => e);

    expect(falla).toBeInstanceOf(ErrorApi);
    expect((falla as ErrorApi).codigo).toBe('NO_AUTENTICADO');
  });
});
