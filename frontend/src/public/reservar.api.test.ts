import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../api/cliente';
import { ejemplo } from '../admin/configuracion/ejemplos.testutil';
import { respuesta, stubServidor } from '../admin/configuracion/servidor.testutil';
import type { ReservaPublicaRequest } from '../api/types';
import { listarPrestacionesPublicas, obtenerDisponibilidad, reservarTurno } from './reservar.api';

const REQUEST = ejemplo('turnos-publico-reservar.request') as ReservaPublicaRequest;

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('listarPrestacionesPublicas', () => {
  it('pide GET /api/public/prestaciones y parsea el ejemplo del contrato', async () => {
    const { llamadas } = stubServidor({ 'GET /api/public/prestaciones': () => respuesta(200, ejemplo('prestaciones-publicas-listar.200')) });
    const lista = await listarPrestacionesPublicas();
    expect(llamadas).toHaveLength(1);
    expect(lista).toEqual([
      { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
      { id: 2, nombre: 'Limpieza', duracionMinutos: 45 },
    ]);
  });

  it('acepta una lista vacia', async () => {
    stubServidor({ 'GET /api/public/prestaciones': () => respuesta(200, []) });
    await expect(listarPrestacionesPublicas()).resolves.toEqual([]);
  });

  it('rechaza elementos sin duracionMinutos', async () => {
    stubServidor({ 'GET /api/public/prestaciones': () => respuesta(200, [{ id: 1, nombre: 'Consulta' }]) });
    await expect(listarPrestacionesPublicas()).rejects.toThrow(/inv/i);
  });

  it('rechaza una respuesta que no es lista', async () => {
    stubServidor({ 'GET /api/public/prestaciones': () => respuesta(200, { id: 1 }) });
    await expect(listarPrestacionesPublicas()).rejects.toThrow(/inv/i);
  });
});

describe('obtenerDisponibilidad', () => {
  const RUTA = 'GET /api/public/disponibilidad?prestacionId=1&desde=2026-12-01&hasta=2026-12-01';

  it('pide el dia con desde = hasta y parsea el ejemplo (incluido el dia sin horarios)', async () => {
    const { llamadas } = stubServidor({ [RUTA]: () => respuesta(200, ejemplo('disponibilidad-publica.200')) });
    const disponibilidad = await obtenerDisponibilidad(1, '2026-12-01');
    expect(llamadas[0]?.url).toBe('/api/public/disponibilidad?prestacionId=1&desde=2026-12-01&hasta=2026-12-01');
    expect(disponibilidad.prestacion).toEqual({ id: 1, nombre: 'Consulta', duracionMinutos: 30 });
    expect(disponibilidad.dias).toEqual([
      { fecha: '2026-12-01', horarios: ['2026-12-01T09:00:00-03:00', '2026-12-01T09:30:00-03:00', '2026-12-01T10:00:00-03:00'] },
      { fecha: '2026-12-02', horarios: [] },
    ]);
  });

  it.each([
    ['horarios no es lista', { horarios: 'x' }],
    ['horarios con elementos que no son texto', { horarios: [1, 2] }],
    ['sin horarios', {}],
  ])('rechaza un dia donde %s', async (_caso, dia) => {
    stubServidor({
      [RUTA]: () =>
        respuesta(200, { prestacion: { id: 1, nombre: 'Consulta', duracionMinutos: 30 }, dias: [{ fecha: '2026-12-01', ...dia }] }),
    });
    await expect(obtenerDisponibilidad(1, '2026-12-01')).rejects.toThrow(/inv/i);
  });

  it('propaga el 422 PRESTACION_INACTIVA como ErrorApi con su codigo', async () => {
    stubServidor({ [RUTA]: () => respuesta(422, ejemplo('disponibilidad-publica.422-prestacion-inactiva')) });
    const error: unknown = await obtenerDisponibilidad(1, '2026-12-01').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ErrorApi);
    expect((error as ErrorApi).codigo).toBe('PRESTACION_INACTIVA');
  });
});

describe('reservarTurno', () => {
  it('envia POST /api/public/turnos con el body y parsea el 201 del ejemplo', async () => {
    const { llamadas } = stubServidor({ 'POST /api/public/turnos': () => respuesta(201, ejemplo('turnos-publico-reservar.201')) });
    const resumen = await reservarTurno(REQUEST);
    expect(llamadas[0]?.body).toEqual(REQUEST);
    expect(resumen).toMatchObject({
      prestacion: { nombre: 'Consulta', duracionMinutos: 30 },
      inicio: '2026-12-10T10:00:00-03:00',
      estado: 'PENDIENTE',
      nombrePaciente: 'Ana',
      contactoConsultorio: 'Consultorio de ejemplo - Tel. +54 9 11 0000-0000',
    });
  });

  it('rechaza un 201 sin estado o con prestacion mal formada', async () => {
    const base = ejemplo('turnos-publico-reservar.201') as Record<string, unknown>;
    const sinEstado = Object.fromEntries(Object.entries(base).filter(([clave]) => clave !== 'estado'));
    stubServidor({ 'POST /api/public/turnos': [() => respuesta(201, sinEstado), () => respuesta(201, { ...base, prestacion: 'Consulta' })] });
    await expect(reservarTurno(REQUEST)).rejects.toThrow(/inv/i);
    await expect(reservarTurno(REQUEST)).rejects.toThrow(/inv/i);
  });

  it.each([
    [409, 'turnos-publico-reservar.409-horario-ocupado', 'HORARIO_OCUPADO'],
    [422, 'turnos-publico-reservar.422-horario-no-disponible', 'HORARIO_NO_DISPONIBLE'],
    [422, 'turnos-publico-reservar.422-consentimiento-requerido', 'CONSENTIMIENTO_REQUERIDO'],
    [422, 'turnos-publico-reservar.422-prestacion-inactiva', 'PRESTACION_INACTIVA'],
    [400, 'error.400-validacion', 'VALIDACION'],
  ] as const)('propaga el %i %s como ErrorApi %s', async (status, archivo, codigo) => {
    stubServidor({ 'POST /api/public/turnos': () => respuesta(status, ejemplo(archivo)) });
    const error: unknown = await reservarTurno(REQUEST).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ErrorApi);
    expect((error as ErrorApi).codigo).toBe(codigo);
  });

  it('el 400 VALIDACION conserva los detalles por campo', async () => {
    stubServidor({ 'POST /api/public/turnos': () => respuesta(400, ejemplo('error.400-validacion')) });
    const error = (await reservarTurno(REQUEST).catch((e: unknown) => e)) as ErrorApi;
    expect(error.detalles?.map((d) => d.campo)).toEqual(['dni', 'email']);
  });
});
