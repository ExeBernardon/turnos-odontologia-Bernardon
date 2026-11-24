import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { crearRouter, type ContextoRuta, type Router, type TablaRutas } from './router';

function pantalla(texto: string) {
  return (contenedor: HTMLElement): void => {
    contenedor.textContent = texto;
  };
}

describe('router', () => {
  let contenedor: HTMLElement;
  let router: Router | null = null;

  const montar = (base: string, raiz: string, rutas: TablaRutas): Router => {
    router = crearRouter({ base, raiz, rutas, contenedor });
    router.iniciar();
    return router;
  };

  beforeEach(() => {
    document.body.innerHTML = '<div id="app"></div>';
    contenedor = document.getElementById('app') as HTMLElement;
  });

  afterEach(() => {
    router?.detener();
    router = null;
  });

  it('renderiza la pantalla registrada para la URL actual', () => {
    window.history.replaceState({}, '', '/admin/pacientes');
    montar('/admin', '/agenda', { '/agenda': pantalla('Agenda'), '/pacientes': pantalla('Pacientes') });
    expect(contenedor.textContent).toBe('Pacientes');
  });

  it('la raiz del area redirige a su pantalla inicial (publica y privada)', () => {
    window.history.replaceState({}, '', '/');
    montar('', '/reservar', { '/reservar': pantalla('Reservar') });
    expect(window.location.pathname).toBe('/reservar');
    expect(contenedor.textContent).toBe('Reservar');
    router?.detener();

    window.history.replaceState({}, '', '/admin/');
    montar('/admin', '/agenda', { '/agenda': pantalla('Agenda') });
    expect(window.location.pathname).toBe('/admin/agenda');
    expect(contenedor.textContent).toBe('Agenda');
  });

  it('muestra "no encontrada" para una ruta desconocida', () => {
    window.history.replaceState({}, '', '/admin/inexistente');
    montar('/admin', '/agenda', { '/agenda': pantalla('Agenda') });
    expect(contenedor.textContent).toContain('no encontrada');
  });

  it('navegar cambia la URL con pushState y muestra la pantalla sin recargar', () => {
    window.history.replaceState({}, '', '/admin/agenda');
    const r = montar('/admin', '/agenda', { '/agenda': pantalla('Agenda'), '/pacientes': pantalla('Pacientes') });
    r.navegar('/pacientes');
    expect(window.location.pathname).toBe('/admin/pacientes');
    expect(contenedor.textContent).toBe('Pacientes');
  });

  it('popstate (atras/adelante) vuelve a mostrar la pantalla de la URL', () => {
    window.history.replaceState({}, '', '/admin/agenda');
    const r = montar('/admin', '/agenda', { '/agenda': pantalla('Agenda'), '/pacientes': pantalla('Pacientes') });
    r.navegar('/pacientes');
    window.history.replaceState({}, '', '/admin/agenda');
    window.dispatchEvent(new PopStateEvent('popstate'));
    expect(contenedor.textContent).toBe('Agenda');
  });

  it('la pantalla recibe la query en el ContextoRuta', () => {
    let recibido: ContextoRuta | null = null;
    window.history.replaceState({}, '', '/turno/cancelar?token=abc');
    montar('', '/reservar', {
      '/reservar': pantalla('Reservar'),
      '/turno/cancelar': (_c, ctx) => {
        recibido = ctx;
      },
    });
    expect(recibido).not.toBeNull();
    expect((recibido as unknown as ContextoRuta).query.get('token')).toBe('abc');
    expect((recibido as unknown as ContextoRuta).ruta).toBe('/turno/cancelar');
  });

  it('un click en <a data-link> navega sin recargar y conserva la query', () => {
    window.history.replaceState({}, '', '/admin/agenda');
    montar('/admin', '/agenda', { '/agenda': pantalla('Agenda'), '/configuracion': pantalla('Config') });
    const a = document.createElement('a');
    a.setAttribute('data-link', '');
    a.setAttribute('href', '/admin/configuracion?x=1');
    document.body.appendChild(a);
    a.click();
    expect(window.location.pathname + window.location.search).toBe('/admin/configuracion?x=1');
    expect(contenedor.textContent).toBe('Config');
  });

  it('un link sin data-link no es interceptado', () => {
    window.history.replaceState({}, '', '/admin/agenda');
    montar('/admin', '/agenda', { '/agenda': pantalla('Agenda') });
    const a = document.createElement('a');
    a.setAttribute('href', '#');
    document.body.appendChild(a);
    const evento = new MouseEvent('click', { bubbles: true, cancelable: true });
    a.dispatchEvent(evento);
    expect(evento.defaultPrevented).toBe(false);
  });
});
