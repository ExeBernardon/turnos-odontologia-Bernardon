import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ejemplo } from './ejemplos.testutil';
import { actualizarPrestacion, crearPrestacion, eliminarPrestacion, listarPrestaciones } from './prestaciones.api';

function resp(status: number, cuerpo?: unknown): Response {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status });
}

interface Req {
  nombre: string;
  duracionMinutos: number;
  activa: boolean;
}

describe('prestaciones.api', () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('listar parsea el ejemplo del contrato (activas e inactivas)', async () => {
    fetchMock.mockResolvedValue(resp(200, ejemplo('prestaciones-listar.200')));
    const r = await listarPrestaciones();
    expect(r).toHaveLength(3);
    expect(r[2]).toEqual({ id: 3, nombre: 'Control de ortodoncia', duracionMinutos: 60, activa: false });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/prestaciones');
  });

  it('listar rechaza una respuesta que no cumple el contrato', async () => {
    fetchMock.mockResolvedValue(resp(200, [{ id: 'x' }]));
    await expect(listarPrestaciones()).rejects.toThrow();
  });

  it('crear envia POST con el request del contrato y parsea el 201', async () => {
    fetchMock.mockResolvedValue(resp(201, ejemplo('prestaciones-crear.201')));
    const req = ejemplo('prestaciones-crear.request') as Req;
    const r = await crearPrestacion(req);
    expect(r.nombre).toBe('Consulta');
    const init = fetchMock.mock.calls[0]?.[1];
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual(req);
  });

  it('actualizar envia PUT a /api/prestaciones/{id}', async () => {
    fetchMock.mockResolvedValue(resp(200, ejemplo('prestaciones-actualizar.200')));
    await actualizarPrestacion(1, ejemplo('prestaciones-actualizar.request') as Req);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/prestaciones/1');
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('PUT');
  });

  it('eliminar resuelve con 204 y propaga el 422 PRESTACION_CON_TURNOS', async () => {
    fetchMock.mockResolvedValueOnce(resp(204));
    await expect(eliminarPrestacion(2)).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('DELETE');

    fetchMock.mockResolvedValueOnce(resp(422, ejemplo('prestaciones-eliminar.422-prestacion-con-turnos')));
    await expect(eliminarPrestacion(1)).rejects.toMatchObject({ status: 422, codigo: 'PRESTACION_CON_TURNOS' });
  });
});
