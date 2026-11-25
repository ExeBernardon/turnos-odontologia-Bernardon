import { describe, expect, it } from 'vitest';
import type { HorarioAtencion } from '../../api/types';
import { agruparPorDia, validarRango } from './horarios.logica';
import { ejemplo } from './ejemplos.testutil';

const listado = ejemplo('horarios-listar.200');

describe('validarRango', () => {
  it('acepta inicio < fin', () => {
    expect(validarRango({ diaSemana: '1', horaInicio: '09:00', horaFin: '13:00' })).toEqual({
      ok: true,
      valor: { diaSemana: 1, horaInicio: '09:00', horaFin: '13:00' },
    });
  });

  it('rechaza inicio igual o posterior a fin', () => {
    for (const [i, f] of [['13:00', '13:00'], ['14:00', '09:00']] as const) {
      const r = validarRango({ diaSemana: '2', horaInicio: i, horaFin: f });
      expect(r.ok).toBe(false);
      if (!r.ok) {
        expect(r.errores.horaFin).toMatch(/posterior|anterior/i);
      }
    }
  });

  it('rechaza horas vacias y dia fuera de 1..7', () => {
    const r = validarRango({ diaSemana: '8', horaInicio: '', horaFin: '' });
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(Object.keys(r.errores).sort()).toEqual(['diaSemana', 'horaFin', 'horaInicio']);
    }
  });
});

describe('agruparPorDia', () => {
  it('devuelve siempre 7 dias de lunes a domingo, con los rangos del ejemplo del contrato', () => {
    const grupos = agruparPorDia(listado as HorarioAtencion[]);
    expect(grupos.map((g) => g.dia)).toEqual([1, 2, 3, 4, 5, 6, 7]);
    expect(grupos[0]?.nombre).toBe('Lunes');
    expect(grupos[0]?.rangos.map((r) => r.id)).toEqual([1, 2]);
    expect(grupos[1]?.rangos.map((r) => r.id)).toEqual([3]);
    expect(grupos[2]?.rangos).toEqual([]);
  });

  it('ordena por hora de inicio dentro del dia, sin mutar la entrada', () => {
    const entrada: HorarioAtencion[] = [
      { id: 9, diaSemana: 7, horaInicio: '15:00', horaFin: '16:00' },
      { id: 8, diaSemana: 7, horaInicio: '08:00', horaFin: '09:00' },
    ];
    const grupos = agruparPorDia(entrada);
    expect(grupos[6]?.rangos.map((r) => r.id)).toEqual([8, 9]);
    expect(entrada[0]?.id).toBe(9);
  });
});
