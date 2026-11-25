import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, ErrorConexion, registrarManejadorExpiracion } from '../../api/cliente';
import { borrarSesion, guardarSesion } from '../../shared/sesion';
import { ejemplo } from '../configuracion/ejemplos.testutil';
import {
  cargarDatosAgenda,
  ErrorRespuestaAgenda,
  listarBloqueosAgenda,
  listarHorariosAgenda,
  listarTurnosAgenda,
} from './agenda.api';

function resp(status: number, cuerpo?: unknown): Response {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function error401(): unknown {
  return {
    timestamp: '2026-11-25T10:00:00-03:00',
    status: 401,
    error: 'Unauthorized',
    codigo: 'NO_AUTENTICADO',
    mensaje: 'Token inválido o expirado.',
    path: '/api/turnos',
  };
}

const rango = { desde: '2026-12-01', hasta: '2026-12-07' };

describe('agenda.api', () => {
  const fetchMock = vi.fn<typeof fetch>();
  const expiracion = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    expiracion.mockReset();
    registrarManejadorExpiracion(expiracion);
    guardarSesion({ token: 'token-ficticio', tipo: 'Bearer' });
  });

  afterEach(() => {
    registrarManejadorExpiracion(null);
    vi.unstubAllGlobals();
    borrarSesion();
  });

  function autorizacion(indice: number): string | null {
    const init = fetchMock.mock.calls[indice]?.[1];
    return init?.headers instanceof Headers ? init.headers.get('Authorization') : null;
  }

  it('pide turnos con desde/hasta, adjunta el token y parsea el ejemplo del contrato', async () => {
    fetchMock.mockResolvedValue(resp(200, ejemplo('turnos-listar.200')));
    const turnos = await listarTurnosAgenda(rango);
    expect(turnos).toHaveLength(3);
    expect(turnos[0]).toMatchObject({ id: 102, estado: 'PENDIENTE', pendienteMenos24h: true });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/turnos?desde=2026-12-01&hasta=2026-12-07');
    expect(autorizacion(0)).toBe('Bearer token-ficticio');
  });

  it('pide bloqueos y horarios con los ejemplos del contrato', async () => {
    fetchMock.mockResolvedValueOnce(resp(200, ejemplo('bloqueos-listar.200')));
    const bloqueos = await listarBloqueosAgenda(rango);
    expect(bloqueos).toEqual([
      {
        id: 1,
        inicio: '2026-12-24T09:00:00-03:00',
        fin: '2026-12-24T13:00:00-03:00',
        motivo: 'Capacitación',
        creadoEn: '2026-11-23T09:00:00-03:00',
      },
    ]);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/bloqueos?desde=2026-12-01&hasta=2026-12-07');

    fetchMock.mockResolvedValueOnce(resp(200, ejemplo('horarios-listar.200')));
    const horarios = await listarHorariosAgenda();
    expect(horarios).toHaveLength(3);
    expect(horarios[0]).toEqual({ id: 1, diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' });
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/horarios-atencion');
    expect(autorizacion(1)).toBe('Bearer token-ficticio');
  });

  it('ignora elementos con campos inválidos sin romper la lista', async () => {
    const valido = (ejemplo('turnos-listar.200') as unknown[])[0];
    fetchMock.mockResolvedValueOnce(resp(200, [{ id: 1, inicio: '2026-12-01T09:00:00-03:00' }, valido]));
    const turnos = await listarTurnosAgenda(rango);
    expect(turnos).toHaveLength(1);
    expect(turnos[0]?.id).toBe(102);

    fetchMock.mockResolvedValueOnce(resp(200, [{ id: 'x' }, { id: 1, diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' }]));
    const horarios = await listarHorariosAgenda();
    expect(horarios).toHaveLength(1);
    expect(horarios[0]?.id).toBe(1);
  });

  it('una respuesta con forma inesperada produce un error tipado', async () => {
    fetchMock.mockResolvedValue(resp(200, { turnos: [] }));
    await expect(listarTurnosAgenda(rango)).rejects.toBeInstanceOf(ErrorRespuestaAgenda);
  });

  it('401 NO_AUTENTICADO propaga ErrorApi y avisa al manejo de sesión', async () => {
    fetchMock.mockResolvedValue(resp(401, error401()));
    const error: unknown = await listarTurnosAgenda(rango).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ErrorApi);
    expect(error).toMatchObject({ status: 401, codigo: 'NO_AUTENTICADO' });
    expect(expiracion).toHaveBeenCalled();
  });

  it('error de conexión propaga ErrorConexion en lugar de la respuesta cruda', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(listarTurnosAgenda(rango)).rejects.toBeInstanceOf(ErrorConexion);
  });

  it('cargarDatosAgenda conserva las secciones que llegaron y reporta las fallas', async () => {
    fetchMock
      .mockResolvedValueOnce(resp(200, ejemplo('turnos-listar.200')))
      .mockResolvedValueOnce(resp(200, ejemplo('bloqueos-listar.200')))
      .mockResolvedValueOnce(resp(500, { mensaje: 'falló' }));

    const datos = await cargarDatosAgenda(rango);
    expect(datos.turnos).toHaveLength(3);
    expect(datos.bloqueos).toHaveLength(1);
    expect(datos.horarios).toBeNull();
    expect(datos.errores).toHaveLength(1);
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('cargarDatosAgenda no devuelve datos parciales ante 401', async () => {
    fetchMock
      .mockResolvedValueOnce(resp(200, ejemplo('turnos-listar.200')))
      .mockResolvedValueOnce(resp(200, ejemplo('bloqueos-listar.200')))
      .mockResolvedValueOnce(resp(401, error401()));

    await expect(cargarDatosAgenda(rango)).rejects.toMatchObject({ status: 401, codigo: 'NO_AUTENTICADO' });
    expect(expiracion).toHaveBeenCalled();
  });
});
