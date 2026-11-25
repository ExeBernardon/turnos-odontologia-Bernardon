import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { rutasAdmin } from './admin/rutas';
import { rutasPublicas } from './public/rutas';
import { crearRouter, type ContextoRuta, type Router, type TablaRutas } from './shared/router';
import { borrarSesion, guardarSesion, obtenerSesion, sesionValida } from './shared/sesion';

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
  let router: Router | null = null;

  beforeEach(() => {
    document.body.innerHTML = '';
    borrarSesion();
  });

  afterEach(() => {
    router?.detener();
    router = null;
    borrarSesion();
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

  it.each([
    ['/agenda', 'Agenda'],
    ['/configuracion', 'Configuración'],
    ['/pacientes', 'Pacientes'],
  ])('el layout marca %s como seccion activa', (ruta, etiqueta) => {
    const contenedor = renderizar(rutasAdmin, ruta);
    const activo = contenedor.querySelector('.admin-nav a[aria-current="page"]');
    expect(activo?.textContent).toBe(etiqueta);
  });

  it('el layout ofrece los tres enlaces del area admin', () => {
    const enlaces = renderizar(rutasAdmin, '/agenda').querySelectorAll('.admin-nav a');
    expect([...enlaces].map((a) => a.getAttribute('href'))).toEqual([
      '/admin/agenda',
      '/admin/configuracion',
      '/admin/pacientes',
      '/admin/login',
    ]);
  });

  it('un cambio de seccion del layout navega sin recargar', () => {
    guardarSesion({ token: 'token-ficticio', tipo: 'Bearer', expiraEn: '2099-12-01T20:00:00-03:00' });
    document.body.innerHTML = '<div id="app"></div>';
    const contenedor = document.getElementById('app') as HTMLElement;
    window.history.replaceState({}, '', '/admin/agenda');
    router = crearRouter({
      base: '/admin',
      raiz: '/agenda',
      rutas: rutasAdmin,
      contenedor,
      redirigir: (ruta) => (ruta === '/login' || sesionValida() ? null : '/login'),
    });
    router.iniciar();

    contenedor.querySelector<HTMLAnchorElement>('a[href="/admin/configuracion"]')?.click();

    expect(window.location.pathname).toBe('/admin/configuracion');
    expect(contenedor.querySelector('h1')?.textContent).toBe('Configuración');
  });

  it('cerrar sesion borra la sesion guardada', () => {
    guardarSesion({ token: 'token-ficticio', tipo: 'Bearer', expiraEn: '2099-12-01T20:00:00-03:00' });
    const contenedor = renderizar(rutasAdmin, '/agenda');
    contenedor.querySelector<HTMLAnchorElement>('.admin-nav__salir')?.click();
    expect(obtenerSesion()).toBeNull();
  });

  it('la pantalla de login del area admin no muestra el layout', () => {
    const contenedor = renderizar(rutasAdmin, '/login');
    expect(contenedor.querySelector('.admin-nav')).toBeNull();
    expect(contenedor.querySelector('h1')?.textContent).toBe('Ingresar');
  });
});
