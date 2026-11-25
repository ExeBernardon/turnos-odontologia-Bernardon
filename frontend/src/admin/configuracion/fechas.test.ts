import { describe, expect, it } from 'vitest';
import { aInstante, diasInclusive, formatear, hoyBA, sumarDias } from './fechas';

describe('aInstante (hora de Buenos Aires a ISO con offset)', () => {
  it('agrega segundos y offset -03:00', () => {
    expect(aInstante('2026-12-24T09:00')).toBe('2026-12-24T09:00:00-03:00');
  });

  it('respeta los limites del dia', () => {
    expect(aInstante('2026-12-24T00:00')).toBe('2026-12-24T00:00:00-03:00');
    expect(aInstante('2026-12-31T23:59')).toBe('2026-12-31T23:59:00-03:00');
  });

  it('acepta segundos si vienen', () => {
    expect(aInstante('2026-12-24T09:30:15')).toBe('2026-12-24T09:30:15-03:00');
  });

  it('devuelve null para vacio o formato invalido', () => {
    expect(aInstante('')).toBeNull();
    expect(aInstante('24/12/2026 09:00')).toBeNull();
    expect(aInstante('2026-13-40T09:00')).toBeNull();
    expect(aInstante('2026-12-24T25:00')).toBeNull();
  });
});

describe('formatear (siempre en America/Argentina/Buenos_Aires)', () => {
  it('convierte un instante UTC a hora de Buenos Aires', () => {
    const texto = formatear('2026-12-24T12:00:00Z');
    expect(texto).toMatch(/24\/12\/2026/);
    expect(texto).toMatch(/09:00/);
  });

  it('respeta el offset del instante y cruza el limite del dia', () => {
    const texto = formatear('2026-12-25T01:30:00Z');
    expect(texto).toMatch(/24\/12\/2026/);
    expect(texto).toMatch(/22:30/);
  });

  it('un instante con offset -03:00 se muestra tal cual', () => {
    expect(formatear('2026-12-24T13:00:00-03:00')).toMatch(/24\/12\/2026.*13:00/);
  });

  it('devuelve el texto original si no es una fecha valida', () => {
    expect(formatear('no-es-fecha')).toBe('no-es-fecha');
  });
});

describe('hoyBA / sumarDias / diasInclusive', () => {
  it('hoyBA usa la fecha de Buenos Aires, no la UTC', () => {
    expect(hoyBA(new Date('2026-11-25T02:00:00Z'))).toBe('2026-11-24');
    expect(hoyBA(new Date('2026-11-25T03:00:00Z'))).toBe('2026-11-25');
  });

  it('sumarDias cruza fin de mes y de anio', () => {
    expect(sumarDias('2026-11-24', 30)).toBe('2026-12-24');
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01');
    expect(sumarDias('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('diasInclusive cuenta ambos extremos', () => {
    expect(diasInclusive('2026-11-24', '2026-11-24')).toBe(1);
    expect(diasInclusive('2026-11-24', '2026-12-24')).toBe(31);
    expect(diasInclusive('2026-12-24', '2026-11-24')).toBe(-29);
    expect(diasInclusive('2026-12-24', '2026-12-23')).toBe(0);
  });
});
