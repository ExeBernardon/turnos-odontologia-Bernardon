import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../../api/cliente';
import type { TurnoManualRequest } from '../../api/types';
import { ErrorRespuestaAgenda } from '../agenda/agenda.api';
import { ejemplo } from '../configuracion/ejemplos.testutil';
import { respuesta, stubServidor } from '../configuracion/servidor.testutil';
import {
  cancelarTurno,
  confirmarTurno,
  crearTurnoManual,
  listarPrestacionesActivas,
  marcarAsistencia,
  obtenerDisponibilidadAdmin,
  reprogramarTurno,
} from './acciones.api';

afterEach(() => vi.unstubAllGlobals());

describe('acciones sobre un turno', () => {
  it('confirmarTurno hace POST …/confirmar y parsea el turno', async () => {
    const { llamadas } = stubServidor({
      'POST /api/turnos/103/confirmar': () => respuesta(200, ejemplo('turnos-confirmar.200')),
    });
    const turno = await confirmarTurno(103);
    expect(llamadas).toEqual([{ metodo: 'POST', url: '/api/turnos/103/confirmar', body: undefined }]);
    expect(turno.estado).toBe('CONFIRMADO');
    expect(turno.paciente.apellido).toBe('Ejemplo');
  });

  it('cancelarTurno hace POST …/cancelar', async () => {
    const { llamadas } = stubServidor({
      'POST /api/turnos/102/cancelar': () => respuesta(200, ejemplo('turnos-cancelar.200')),
    });
    const turno = await cancelarTurno(102);
    expect(llamadas[0]?.metodo).toBe('POST');
    expect(turno.estado).toBe('CANCELADO');
    expect(turno.canceladoPor).toBe('ODONTOLOGO');
  });

  it('marcarAsistencia hace PATCH …/estado con el estado elegido', async () => {
    const { llamadas } = stubServidor({
      'PATCH /api/turnos/103/estado': () => respuesta(200, ejemplo('turnos-estado.200')),
    });
    const turno = await marcarAsistencia(103, 'ASISTIO');
    expect(llamadas[0]?.body).toEqual(ejemplo('turnos-estado.request'));
    expect(turno.estado).toBe('ASISTIO');
    await marcarAsistencia(103, 'AUSENTE');
    expect(llamadas[1]?.body).toEqual({ estado: 'AUSENTE' });
  });

  it('reprogramarTurno hace PATCH …/reprogramar con el nuevo inicio', async () => {
    const { llamadas } = stubServidor({
      'PATCH /api/turnos/101/reprogramar': () => respuesta(200, ejemplo('turnos-reprogramar.200')),
    });
    const turno = await reprogramarTurno(101, '2026-12-01T18:00:00-03:00');
    expect(llamadas[0]?.body).toEqual(ejemplo('turnos-reprogramar.request'));
    expect(turno.inicio).toBe('2026-12-01T18:00:00-03:00');
  });

  it('una respuesta sin paciente es una respuesta inválida', async () => {
    const sinPaciente = { ...(ejemplo('turnos-confirmar.200') as Record<string, unknown>) };
    delete sinPaciente['paciente'];
    stubServidor({ 'POST /api/turnos/103/confirmar': () => respuesta(200, sinPaciente) });
    await expect(confirmarTurno(103)).rejects.toBeInstanceOf(ErrorRespuestaAgenda);
  });

  it('propaga la transición inválida como ErrorApi', async () => {
    stubServidor({
      'POST /api/turnos/102/confirmar': () => respuesta(422, ejemplo('turnos-confirmar.422-transicion-invalida')),
    });
    await expect(confirmarTurno(102)).rejects.toMatchObject({ status: 422, codigo: 'TRANSICION_INVALIDA' });
  });
});

describe('alta manual y datos de apoyo', () => {
  const existente = ejemplo('turnos-crear.request-paciente-existente') as TurnoManualRequest;
  const nuevo = ejemplo('turnos-crear.request-paciente-nuevo') as TurnoManualRequest;

  it('crearTurnoManual envía los dos requests de ejemplo y devuelve el turno', async () => {
    const { llamadas } = stubServidor({ 'POST /api/turnos': () => respuesta(201, ejemplo('turnos-crear.201')) });
    const turno = await crearTurnoManual(existente);
    await crearTurnoManual(nuevo);
    expect(llamadas.map((l) => l.body)).toEqual([existente, nuevo]);
    expect(turno.id).toBe(105);
    expect(turno.estado).toBe('PENDIENTE');
  });

  it.each([
    [409, 'turnos-crear.409-horario-ocupado', 'HORARIO_OCUPADO'],
    [422, 'turnos-crear.422-horario-no-disponible', 'HORARIO_NO_DISPONIBLE'],
    [422, 'turnos-crear.422-prestacion-inactiva', 'PRESTACION_INACTIVA'],
    [422, 'turnos-crear.422-dni-duplicado', 'DNI_DUPLICADO'],
  ])('crearTurnoManual propaga el %s %s como ErrorApi', async (status, nombre, codigo) => {
    stubServidor({ 'POST /api/turnos': () => respuesta(status, ejemplo(nombre)) });
    const error: unknown = await crearTurnoManual(existente).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ErrorApi);
    expect(error).toMatchObject({ status, codigo });
  });

  it('obtenerDisponibilidadAdmin usa el endpoint privado y parsea los horarios', async () => {
    const { llamadas } = stubServidor({
      'GET /api/disponibilidad?prestacionId=1&desde=2026-12-01&hasta=2026-12-01': () =>
        respuesta(200, ejemplo('disponibilidad-privada.200')),
    });
    const disponibilidad = await obtenerDisponibilidadAdmin(1, '2026-12-01');
    expect(llamadas[0]?.url).not.toContain('/public/');
    expect(disponibilidad.dias).toEqual([
      { fecha: '2026-12-01', horarios: ['2026-12-01T09:00:00-03:00', '2026-12-01T09:30:00-03:00'] },
    ]);
    expect(disponibilidad.prestacion.duracionMinutos).toBe(30);
  });

  it('obtenerDisponibilidadAdmin propaga la prestación inactiva y rechaza respuestas sin días', async () => {
    stubServidor({
      'GET /api/disponibilidad?prestacionId=3&desde=2026-12-01&hasta=2026-12-01': () =>
        respuesta(422, ejemplo('disponibilidad-privada.422-prestacion-inactiva')),
      'GET /api/disponibilidad?prestacionId=4&desde=2026-12-01&hasta=2026-12-01': () => respuesta(200, { prestacion: {} }),
    });
    await expect(obtenerDisponibilidadAdmin(3, '2026-12-01')).rejects.toMatchObject({ codigo: 'PRESTACION_INACTIVA' });
    await expect(obtenerDisponibilidadAdmin(4, '2026-12-01')).rejects.toBeInstanceOf(Error);
  });

  it('listarPrestacionesActivas descarta las inactivas', async () => {
    stubServidor({ 'GET /api/prestaciones': () => respuesta(200, ejemplo('prestaciones-listar.200')) });
    const activas = await listarPrestacionesActivas();
    expect(activas.map((p) => p.nombre)).toEqual(['Consulta', 'Limpieza']);
  });
});
