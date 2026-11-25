import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(resolve(__dirname, 'acciones.css'), 'utf8');
const sinComentarios = css.replace(/\/\*[\s\S]*?\*\//g, '');

function selectores(): string[] {
  const resultado: string[] = [];
  for (const m of sinComentarios.matchAll(/([^{}]+)\{/g)) {
    const cabecera = (m[1] ?? '').trim();
    if (cabecera.startsWith('@')) {
      continue;
    }
    resultado.push(...cabecera.split(',').map((s) => s.trim()));
  }
  return resultado;
}

describe('acciones.css', () => {
  it('acota todos los selectores a .agenda-acciones y .aa-*', () => {
    const fuera = selectores().filter((s) => !/^\.(aa-|agenda-acciones)/.test(s));
    expect(fuera).toEqual([]);
  });

  it('usa los tokens de la revisión 1 y no define colores fijos', () => {
    expect(css).toContain('var(--color-danger-border)');
    expect(css).toContain('var(--focus-color)');
    expect(css).toContain('var(--control-min-height)');
    expect(sinComentarios).not.toMatch(/#[0-9a-fA-F]{3,6}\b/);
  });

  it('da botones de 44 px o más y foco visible', () => {
    expect(css).toMatch(/\.aa-boton\s*\{[^}]*min-height:\s*var\(--control-min-height\)/);
    expect(css).toMatch(/:focus-visible/);
  });

  it('distingue Cancelar y Ausente por borde además del color', () => {
    expect(css).toMatch(/\.aa-boton--peligro\s*\{[^}]*border:\s*2px solid/);
  });

  it('es mobile-first: diálogo a ancho completo y paneles sin desborde a 320 px', () => {
    expect(css).toMatch(/\.aa-dialogo\s*\{[^}]*width:\s*100%/);
    expect(css).toContain('@media (min-width: 40rem)');
    expect(css).toContain('overflow-wrap: anywhere');
    expect(css).not.toMatch(/\bwidth:\s*\d+px/);
  });
});
