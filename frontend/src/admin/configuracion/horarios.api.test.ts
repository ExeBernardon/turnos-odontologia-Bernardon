import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ejemplo } from './ejemplos.testutil';
import { actualizarHorario, crearHorario, eliminarHorario, listarHorarios } from './horarios.api';

function resp(status: number, cuerpo?: unknown): Response {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status });
}

interface Req {
  diaSemana: number;
  horaInicio: string;
  horaFin: string;
}

describe('horarios.api', () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('listar parsea el ejemplo del contrato', async () => {
    fetchMock.mockResolvedValue(resp(200, ejemplo('horarios-listar.200')));
    const r = await listarHorarios();
    expect(r).toHaveLength(3);
    expect(r[1]).toEqual({ id: 2, diaSemana: 1, horaInicio: '15:00', horaFin: '19:00' });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/horarios-atencion');
  });

  it('listar rechaza datos fuera de contrato', async () => {
    fetchMock.mockResolvedValue(resp(200, [{ id: 1, diaSemana: 1 }]));
    await expect(listarHorarios()).rejects.toThrow();
  });

  it('crear y actualizar usan POST y PUT con el request del contrato', async () => {
    fetchMock.mockResolvedValueOnce(resp(201, ejemplo('horarios-crear.201')));
    const req = ejemplo('horarios-crear.request') as Req;
    await crearHorario(req);
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('POST');
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual(req);

    fetchMock.mockResolvedValueOnce(resp(200, ejemplo('horarios-actualizar.200')));
    await actualizarHorario(1, ejemplo('horarios-actualizar.request') as Req);
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/horarios-atencion/1');
    expect(fetchMock.mock.calls[1]?.[1]?.method).toBe('PUT');
  });

  it('propaga 422 HORARIOS_SOLAPADOS y elimina con 204', async () => {
    fetchMock.mockResolvedValueOnce(resp(422, ejemplo('horarios-crear.422-horarios-solapados')));
    await expect(crearHorario({ diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' })).rejects.toMatchObject({
      codigo: 'HORARIOS_SOLAPADOS',
    });
    fetchMock.mockResolvedValueOnce(resp(204));
    await expect(eliminarHorario(3)).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[1]?.[1]?.method).toBe('DELETE');
  });
});
