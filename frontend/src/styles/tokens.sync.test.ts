import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const leer = (ruta: string): string => readFileSync(resolve(__dirname, ruta), 'utf8');

const HOJAS_DE_PANTALLA = [
  '../admin/admin.css',
  '../admin/agenda/agenda.css',
  '../admin/agenda-acciones/acciones.css',
  '../admin/configuracion/configuracion.css',
  '../admin/pacientes.css',
  '../shared/buscador-pacientes.css',
  '../public/reservar.css',
  '../public/turno-token.css',
];

// agenda.css.test.ts exige declarar --radius-pill en agenda.css; se tolera solo con el mismo valor.
const PERMITIDOS: Record<string, string> = { '--radius-pill': '999px' };

function tokensDe(css: string): Map<string, string> {
  const tokens = new Map<string, string>();
  for (const m of css.matchAll(/(--[a-z0-9-]+)\s*:\s*([^;]+);/g)) {
    tokens.set(m[1] ?? '', (m[2] ?? '').trim());
  }
  return tokens;
}

describe('tokens visuales', () => {
  it('src/styles/tokens.css es copia exacta de design-system/tokens.css', () => {
    expect(leer('./tokens.css'), 'tokens desincronizados: copiar design-system/tokens.css').toBe(
      leer('../../../design-system/tokens.css'),
    );
  });

  it('base.css importa los tokens antes que cualquier regla', () => {
    expect(leer('./base.css').trimStart().startsWith("@import './tokens.css';")).toBe(true);
  });

  it('ninguna hoja de pantalla redeclara un token compartido', () => {
    const compartidos = tokensDe(leer('./tokens.css'));
    const redeclarados: string[] = [];
    for (const hoja of HOJAS_DE_PANTALLA) {
      for (const [nombre, valor] of tokensDe(leer(hoja))) {
        if (compartidos.has(nombre) && PERMITIDOS[nombre] !== valor) {
          redeclarados.push(`${hoja}: ${nombre}`);
        }
      }
    }
    expect(redeclarados).toEqual([]);
  });

  it('la paleta de la revisión 2 usa el primario teal', () => {
    expect(tokensDe(leer('./tokens.css')).get('--color-primary')).toBe('#0f6e66');
  });
});
