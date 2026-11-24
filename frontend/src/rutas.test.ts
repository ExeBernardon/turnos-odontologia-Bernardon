import { beforeEach, describe, expect, it } from 'vitest';
import { rutasAdmin } from './admin/rutas';
import { rutasPublicas } from './public/rutas';
import type { ContextoRuta, TablaRutas } from './shared/router';

const ctx = (ruta: string, busqueda = ''): ContextoRuta => ({ ruta, query: new URLSearchParams(busqueda) });

function renderizar(tabla: TablaRutas, ruta: string, busqueda = ''): HTMLElement {
  const contenedor = document.createElement('div');
  const pantalla = tabla[ruta];
  if (pantalla === undefined) {
    throw new Error(`ruta no registrada: ${ruta}`);
  }
  pantalla(contenedor, ctx(ruta, busqueda));
  return contenedor;
}

describe('rutas del MVP', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  it.each([
    ['/reservar', 'Reservar turno'],
    ['/turno/confirmar', 'Confirmar turno'],
    ['/turno/cancelar', 'Cancelar turno'],
  ])('area publica: %s muestra su encabezado', (ruta, titulo) => {
    expect(renderizar(rutasPublicas, ruta).querySelector('h1')?.textContent).toBe(titulo);
  });

  it.each([
    ['/login', 'Ingresar'],
    ['/agenda', 'Agenda'],
    ['/configuracion', 'Configuración'],
    ['/pacientes', 'Pacientes'],
  ])('area privada: %s muestra su encabezado', (ruta, titulo) => {
    expect(renderizar(rutasAdmin, ruta).querySelector('h1')?.textContent).toBe(titulo);
  });

  it('configuracion se compone de tres secciones independientes con su encabezado', () => {
    const c = renderizar(rutasAdmin, '/configuracion');
    const encabezados = [...c.querySelectorAll('section h2')].map((h) => h.textContent);
    expect(encabezados).toEqual(['Prestaciones', 'Horarios de atención', 'Bloqueos']);
  });

  it('registra exactamente las rutas del MVP, sin extras', () => {
    expect(Object.keys(rutasPublicas).sort()).toEqual(['/reservar', '/turno/cancelar', '/turno/confirmar']);
    expect(Object.keys(rutasAdmin).sort()).toEqual(['/agenda', '/configuracion', '/login', '/pacientes']);
  });
});
