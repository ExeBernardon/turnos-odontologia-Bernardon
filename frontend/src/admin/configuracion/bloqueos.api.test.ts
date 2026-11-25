import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../api/cliente';
import { crearBloqueo, eliminarBloqueo, listarBloqueos, previsualizarBloqueo, turnosAfectadosDe } from './bloqueos.api';
import { ejemplo } from './ejemplos.testutil';

function resp(status: number, cuerpo?: unknown): Response {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status });
}

interface Req {
  inicio: string;
  fin: string;
  motivo: string;
  cancelarTurnosAfectados: boolean;
}

describe('bloqueos.api', () => {
  const fetchMock = vi.fn<typeof fetch>();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it('listar envia desde y hasta y parsea el ejemplo del contrato', async () => {
    fetchMock.mockResolvedValue(resp(200, ejemplo('bloqueos-listar.200')));
    const r = await listarBloqueos({ desde: '2026-12-01', hasta: '2026-12-31' });
    expect(r).toEqual([
      { id: 1, inicio: '2026-12-24T09:00:00-03:00', fin: '2026-12-24T13:00:00-03:00', motivo: 'Capacitación', creadoEn: '2026-11-23T09:00:00-03:00' },
    ]);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/bloqueos?desde=2026-12-01&hasta=2026-12-31');
  });

  it('listar acepta motivo null y rechaza una respuesta fuera de contrato', async () => {
    fetchMock.mockResolvedValueOnce(resp(200, [{ id: 2, inicio: 'a', fin: 'b', motivo: null, creadoEn: 'c' }]));
    expect((await listarBloqueos({ desde: '2026-12-01', hasta: '2026-12-02' }))[0]?.motivo).toBeNull();
    fetchMock.mockResolvedValueOnce(resp(200, [{ id: 'x' }]));
    await expect(listarBloqueos({ desde: '2026-12-01', hasta: '2026-12-02' })).rejects.toThrow();
  });

  it('previsualizar envia POST al path de vista previa y devuelve los turnos afectados', async () => {
    fetchMock.mockResolvedValue(resp(200, ejemplo('bloqueos-previsualizar.200')));
    const req = ejemplo('bloqueos-previsualizar.request') as Req;
    const r = await previsualizarBloqueo({ inicio: req.inicio, fin: req.fin, motivo: req.motivo });
    expect(r).toHaveLength(2);
    expect(r[1]?.paciente.apellido).toBe('Prueba');
    expect(r[0]?.estado).toBe('PENDIENTE');
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/bloqueos/previsualizar');
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('POST');
  });

  it('crear envia cancelarTurnosAfectados y parsea el 201', async () => {
    fetchMock.mockResolvedValue(resp(201, ejemplo('bloqueos-crear.201')));
    const req = ejemplo('bloqueos-crear.request') as Req;
    const r = await crearBloqueo(req);
    expect(r.bloqueo.id).toBe(1);
    expect(r.turnosCancelados).toHaveLength(2);
    expect(r.turnosCancelados[0]?.estado).toBe('CANCELADO');
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual(req);
  });

  it('crear propaga el 409 con turnosAfectados legibles desde el error', async () => {
    fetchMock.mockResolvedValue(resp(409, ejemplo('bloqueos-crear.409-bloqueo-con-turnos-afectados')));
    const error: unknown = await crearBloqueo({ inicio: 'a', fin: 'b' }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ErrorApi);
    expect(error).toMatchObject({ status: 409, codigo: 'BLOQUEO_CON_TURNOS_AFECTADOS' });
    expect(turnosAfectadosDe(error)).toHaveLength(2);
  });

  it('turnosAfectadosDe devuelve null si no es un 409 de bloqueo o no trae la lista', () => {
    expect(turnosAfectadosDe(new Error('x'))).toBeNull();
    expect(turnosAfectadosDe(new ErrorApi(422, 'm', 'RANGO_HORARIO_INVALIDO'))).toBeNull();
    const sinLista = ejemplo('bloqueos-crear.422-rango-horario-invalido') as never;
    expect(turnosAfectadosDe(new ErrorApi(409, 'm', 'BLOQUEO_CON_TURNOS_AFECTADOS', undefined, sinLista))).toBeNull();
  });

  it('crear propaga el 422 RANGO_HORARIO_INVALIDO', async () => {
    fetchMock.mockResolvedValue(resp(422, ejemplo('bloqueos-crear.422-rango-horario-invalido')));
    await expect(crearBloqueo({ inicio: 'a', fin: 'b' })).rejects.toMatchObject({ status: 422, codigo: 'RANGO_HORARIO_INVALIDO' });
  });

  it('eliminar envia DELETE a /api/bloqueos/{id} y resuelve con 204', async () => {
    fetchMock.mockResolvedValue(resp(204));
    await expect(eliminarBloqueo(7)).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/bloqueos/7');
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('DELETE');
  });
});
