import { describe, expect, it } from 'vitest';
import { validarPrestacion } from './prestaciones.logica';

describe('validarPrestacion', () => {
  it('acepta nombre y duracion validos y normaliza', () => {
    expect(validarPrestacion({ nombre: '  Limpieza ', duracion: '45' })).toEqual({
      ok: true,
      valor: { nombre: 'Limpieza', duracionMinutos: 45 },
    });
  });

  it('acepta los limites 1 y 480', () => {
    expect(validarPrestacion({ nombre: 'A', duracion: '1' }).ok).toBe(true);
    expect(validarPrestacion({ nombre: 'A', duracion: '480' }).ok).toBe(true);
  });

  it.each(['', '0', '-5', '30.5', '481', 'abc', ' '])('rechaza duracion %j', (duracion) => {
    const r = validarPrestacion({ nombre: 'A', duracion });
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.errores.duracionMinutos).toBeDefined();
      expect(r.errores.nombre).toBeUndefined();
    }
  });

  it('rechaza nombre vacio, solo espacios o de mas de 100 caracteres', () => {
    for (const nombre of ['', '   ', 'x'.repeat(101)]) {
      const r = validarPrestacion({ nombre, duracion: '30' });
      expect(r.ok).toBe(false);
      if (!r.ok) {
        expect(r.errores.nombre).toBeDefined();
      }
    }
  });

  it('informa ambos errores a la vez', () => {
    const r = validarPrestacion({ nombre: '', duracion: '0' });
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(Object.keys(r.errores).sort()).toEqual(['duracionMinutos', 'nombre']);
    }
  });
});
