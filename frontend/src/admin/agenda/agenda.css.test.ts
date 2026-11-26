import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(resolve(__dirname, 'agenda.css'), 'utf8');

describe('agenda.css', () => {
  it('usa los tokens de la revisión 1 aprobada', () => {
    expect(css).toContain('var(--color-warning-bg)');
    expect(css).toContain('var(--color-success-bg)');
    expect(css).toContain('var(--focus-color)');
    expect(css).toContain('--radius-pill: 999px');
  });

  it('mantiene foco visible y evita desbordes en 390 px', () => {
    expect(css).toMatch(/\.agenda :focus-visible/);
    expect(css).toContain('@media (max-width: 40rem)');
    expect(css).toContain('grid-template-columns: 1fr');
    expect(css).toContain('overflow-wrap: anywhere');
  });

  it('la vista semanal envuelve columnas legibles y alinea las tarjetas arriba', () => {
    expect(css).toContain('repeat(auto-fill, minmax(12.5rem, 1fr))');
    expect(css).toMatch(/\.agenda-semana \{[^}]*align-items: start/);
    expect(css).toMatch(/\.admin-main:has\(\.agenda-semana\)/);
  });

  it('el badge de estado no se parte en dos líneas', () => {
    expect(css).toMatch(/\.agenda-badge,[^{]*\{[^}]*white-space: nowrap/);
  });

  it('en lista apilada se ocultan las franjas fuera de horario de la semana', () => {
    expect(css).toMatch(/\.agenda-semana \.agenda-franja--sin-atencion \{[^}]*display: none/);
  });
});
