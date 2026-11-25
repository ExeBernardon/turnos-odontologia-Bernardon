import { describe, expect, it } from 'vitest';
import type { Bloqueo, HorarioAtencion, Turno } from '../../api/types';
import { ejemplo } from '../configuracion/ejemplos.testutil';
import {
  agruparTurnos,
  diaSemana,
  esUrgente,
  fechaDeInstante,
  formatearFechaLarga,
  formatearHora,
  franjasBloqueo,
  franjasDelDia,
  huecosSinAtencion,
  inicioSemana,
  minutosDeInstante,
  horaDesdeMinutos,
  rangosAtencion,
  rangoDiario,
  rangoSemanal,
  turnosVisibles,
} from './agenda.logica';

const horarios = ejemplo('horarios-listar.200') as HorarioAtencion[];

function bloqueo(inicio: string, fin: string, motivo: string | null = null): Bloqueo {
  return { id: 1, inicio, fin, motivo, creadoEn: '2026-11-23T09:00:00-03:00' };
}

describe('rangos en Buenos Aires', () => {
  it('el rango diario es un solo día', () => {
    expect(rangoDiario('2026-12-01')).toEqual({ desde: '2026-12-01', hasta: '2026-12-01' });
  });

  it('la semana va de lunes a domingo', () => {
    expect(inicioSemana('2026-12-01')).toBe('2026-11-30');
    expect(inicioSemana('2026-12-06')).toBe('2026-11-30');
    expect(inicioSemana('2026-11-30')).toBe('2026-11-30');
    expect(rangoSemanal('2026-12-01')).toEqual({ desde: '2026-11-30', hasta: '2026-12-06' });
  });

  it('diaSemana usa 1..7 con domingo 7', () => {
    expect(diaSemana('2026-11-30')).toBe(1);
    expect(diaSemana('2026-12-01')).toBe(2);
    expect(diaSemana('2026-12-06')).toBe(7);
  });
});

describe('formato en America/Argentina/Buenos_Aires', () => {
  it('fechaDeInstante no depende del offset recibido', () => {
    expect(fechaDeInstante('2026-12-01T16:00:00-03:00')).toBe('2026-12-01');
    expect(fechaDeInstante('2026-12-01T19:00:00Z')).toBe('2026-12-01');
    expect(fechaDeInstante('2026-12-01T02:00:00Z')).toBe('2026-11-30');
    expect(fechaDeInstante('no-es-fecha')).toBeNull();
  });

  it('formatearHora y minutosDeInstante usan la zona de Buenos Aires', () => {
    expect(formatearHora('2026-12-01T16:00:00-03:00')).toBe('16:00');
    expect(formatearHora('2026-12-01T19:00:00Z')).toBe('16:00');
    expect(minutosDeInstante('2026-12-01T09:30:00-03:00')).toBe(570);
    expect(horaDesdeMinutos(570)).toBe('09:30');
    expect(formatearHora('no-es-fecha')).toBe('');
  });

  it('la etiqueta de fecha incluye día, mes y año en español', () => {
    const texto = formatearFechaLarga('2026-12-01');
    expect(texto).toContain('martes');
    expect(texto).toContain('1 de diciembre de 2026');
  });
});

describe('grilla a partir de horarios y bloqueos', () => {
  it('agrupa los rangos del día y marca los huecos dentro de la ventana', () => {
    const rangos = rangosAtencion('2026-11-30', horarios);
    expect(rangos.map((r) => [horaDesdeMinutos(r.desde), horaDesdeMinutos(r.hasta)])).toEqual([
      ['09:00', '13:00'],
      ['15:00', '19:00'],
    ]);

    const huecos = huecosSinAtencion(rangos);
    expect(huecos.map((h) => [h.tipo, h.inicio, h.fin])).toEqual([
      ['sin-atencion', '08:00', '09:00'],
      ['sin-atencion', '13:00', '15:00'],
      ['sin-atencion', '19:00', '20:00'],
    ]);
  });

  it('superpone bloqueos del día y los ordena con los huecos', () => {
    const bloqueos = [bloqueo('2026-11-30T11:00:00-03:00', '2026-11-30T13:00:00-03:00', 'Capacitación')];
    expect(franjasBloqueo('2026-11-30', bloqueos)).toEqual([
      {
        tipo: 'bloqueo',
        inicio: '11:00',
        fin: '13:00',
        motivo: 'Capacitación',
        inicioMinutos: 660,
      },
    ]);

    const franjas = franjasDelDia('2026-11-30', horarios, bloqueos);
    expect(franjas.map((f) => f.inicio)).toEqual(['08:00', '11:00', '13:00', '19:00']);
    expect(franjas[1]).toMatchObject({ tipo: 'bloqueo', motivo: 'Capacitación' });
  });

  it('un día sin horarios no inventa franjas', () => {
    expect(rangosAtencion('2026-12-05', horarios)).toEqual([]);
    expect(franjasDelDia('2026-12-05', horarios, [])).toEqual([]);
  });
});

describe('turnos de la agenda', () => {
  const turnos = ejemplo('turnos-listar.200') as Turno[];

  it('agrupa turnos por fecha local y los ordena por inicio', () => {
    const porDia = agruparTurnos(turnos, ['2026-12-01', '2026-12-02']);
    expect(porDia.get('2026-12-01')?.map((t) => t.id)).toEqual([102]);
    expect(porDia.get('2026-12-02')?.map((t) => t.id)).toEqual([103, 104]);
  });

  it('oculta cancelados por defecto y detecta urgencia', () => {
    expect(turnosVisibles(turnos, false).map((t) => t.id)).toEqual([102, 103]);
    expect(turnosVisibles(turnos, true).map((t) => t.id)).toEqual([102, 103, 104]);
    expect(esUrgente(turnos[0] as Turno)).toBe(true);
    expect(esUrgente(turnos[1] as Turno)).toBe(false);
  });
});
