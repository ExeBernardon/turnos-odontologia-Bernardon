import { describe, expect, it } from 'vitest';
import { MOTIVO_MAX, RANGO_MAX_DIAS, rangoPorDefecto, validarBloqueo, validarRango } from './bloqueos.logica';

describe('validarBloqueo', () => {
  it('arma el request del contrato con offset -03:00 y motivo recortado', () => {
    const r = validarBloqueo({ inicio: '2026-12-24T09:00', fin: '2026-12-24T13:00', motivo: '  Capacitación ' });
    expect(r).toEqual({
      ok: true,
      valor: { inicio: '2026-12-24T09:00:00-03:00', fin: '2026-12-24T13:00:00-03:00', motivo: 'Capacitación' },
    });
  });

  it('omite el motivo si esta vacio o solo espacios', () => {
    const r = validarBloqueo({ inicio: '2026-12-24T09:00', fin: '2026-12-24T13:00', motivo: '   ' });
    expect(r.ok && 'motivo' in r.valor).toBe(false);
  });

  it('exige inicio y fin', () => {
    const r = validarBloqueo({ inicio: '', fin: '', motivo: '' });
    expect(r).toEqual({ ok: false, errores: { inicio: expect.stringMatching(/inicio/i), fin: expect.stringMatching(/fin/i) } });
  });

  it('rechaza inicio igual o posterior al fin, con el error en fin', () => {
    const igual = validarBloqueo({ inicio: '2026-12-24T09:00', fin: '2026-12-24T09:00', motivo: '' });
    const despues = validarBloqueo({ inicio: '2026-12-24T10:00', fin: '2026-12-24T09:00', motivo: '' });
    for (const r of [igual, despues]) {
      expect(r.ok).toBe(false);
      expect(!r.ok && r.errores.fin).toMatch(/anterior/);
      expect(!r.ok && r.errores.inicio).toBeUndefined();
    }
  });

  it('el motivo admite exactamente 200 caracteres y rechaza 201', () => {
    const base = { inicio: '2026-12-24T09:00', fin: '2026-12-24T13:00' };
    expect(validarBloqueo({ ...base, motivo: 'a'.repeat(MOTIVO_MAX) }).ok).toBe(true);
    const r = validarBloqueo({ ...base, motivo: 'a'.repeat(MOTIVO_MAX + 1) });
    expect(!r.ok && r.errores.motivo).toMatch(/200/);
  });
});

describe('validarRango', () => {
  it('acepta un rango de 31 dias inclusive', () => {
    expect(validarRango({ desde: '2026-11-24', hasta: '2026-12-24' })).toEqual({
      ok: true,
      valor: { desde: '2026-11-24', hasta: '2026-12-24' },
    });
    expect(RANGO_MAX_DIAS).toBe(31);
  });

  it('rechaza 32 dias', () => {
    const r = validarRango({ desde: '2026-11-24', hasta: '2026-12-25' });
    expect(!r.ok && r.errores.hasta).toMatch(/31/);
  });

  it('rechaza campos vacios y desde posterior a hasta', () => {
    const vacio = validarRango({ desde: '', hasta: '' });
    expect(!vacio.ok && Object.keys(vacio.errores).sort()).toEqual(['desde', 'hasta']);
    const invertido = validarRango({ desde: '2026-12-24', hasta: '2026-12-23' });
    expect(!invertido.ok && invertido.errores.hasta).toMatch(/posterior|anterior/);
  });
});

describe('rangoPorDefecto', () => {
  it('va de hoy (Buenos Aires) a 30 dias despues', () => {
    expect(rangoPorDefecto(new Date('2026-11-25T02:00:00Z'))).toEqual({ desde: '2026-11-24', hasta: '2026-12-24' });
  });
});
