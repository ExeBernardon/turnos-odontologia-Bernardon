import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(resolve(__dirname, 'base.css'), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');

function bloque(selector: RegExp): string {
  const m = css.match(new RegExp(`${selector.source}[^{]*\\{([^}]*)\\}`));
  return m?.[1] ?? '';
}

describe('base.css (capa global)', () => {
  it('importa los tokens y no usa colores fijos fuera de sombras', () => {
    expect(css).toContain("@import './tokens.css'");
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/);
  });

  it('da foco visible con el color de foco a todo elemento interactivo', () => {
    expect(bloque(/\n:focus-visible/)).toContain('outline: var(--focus-width) solid var(--focus-color)');
  });

  it('botones y controles miden al menos 44 px', () => {
    expect(bloque(/\nbutton /)).toContain('min-height: var(--control-min-height)');
    expect(bloque(/\n:where\(input/)).toContain('min-height: var(--control-min-height)');
  });

  it('el botón de envío es primario y los demás secundarios, con baja especificidad', () => {
    expect(bloque(/button:where\(\[type='submit'\]\)/)).toContain('background: var(--color-primary)');
    expect(bloque(/\nbutton /)).toContain('background: var(--color-bg)');
  });

  it('deshabilitado y error se distinguen', () => {
    expect(css).toMatch(/button:disabled[^{]*\{[^}]*cursor: not-allowed/);
    expect(css).toMatch(/\[aria-invalid='true'\][^{]*\{[^}]*border-color: var\(--color-danger-border\)/);
  });

  it('respeta prefers-reduced-motion', () => {
    expect(css).toContain('@media (prefers-reduced-motion: reduce)');
    expect(css).toContain('transition-duration: var(--motion-fast)');
  });

  it('evita scroll horizontal en pantallas angostas', () => {
    expect(bloque(/\nbody/)).toContain('overflow-wrap: break-word');
  });

  it('el envío de búsquedas y filtros es secundario (fondo de superficie, no primario)', () => {
    const b = bloque(/:where\(form\[role='search'\], \.cfg-filtro\) button\[type='submit'\]:not\(:disabled\)/);
    expect(b).toContain('background: var(--color-bg)');
    expect(b).not.toContain('--color-primary)');
    expect(css).toMatch(/\.cfg-filtro\) button\[type='submit'\]:hover:not\(:disabled\)/);
  });

  it('las regiones vivas vacías no ocupan margen', () => {
    expect(bloque(/\np:empty/)).toContain('margin: 0');
  });
});
