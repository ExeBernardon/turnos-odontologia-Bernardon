import { el, montar } from '../shared/dom';
import type { Pantalla, TablaRutas } from '../shared/router';
import { borrarSesion } from '../shared/sesion';
import { render as agenda } from './agenda';
import { render as configuracion } from './configuracion';
import { render as login } from './login';
import { render as pacientes } from './pacientes';

export const BASE_ADMIN = '/admin';
export const RAIZ_ADMIN = '/agenda';

const SECCIONES = [
  { ruta: '/agenda', etiqueta: 'Agenda' },
  { ruta: '/configuracion', etiqueta: 'Configuración' },
  { ruta: '/pacientes', etiqueta: 'Pacientes' },
] as const;

function conLayout(rutaActual: string, pantalla: Pantalla): Pantalla {
  return (contenedor, ctx) => {
    const contenido = el('main', { class: 'admin-main' });
    pantalla(contenido, ctx);

    const enlaces = SECCIONES.map(({ ruta, etiqueta }) => {
      const atributos: Record<string, string> = { href: BASE_ADMIN + ruta, 'data-link': '' };
      if (ruta === rutaActual) {
        atributos['aria-current'] = 'page';
      }
      return el('a', atributos, etiqueta);
    });
    const salir = el(
      'a',
      { class: 'admin-nav__salir', href: `${BASE_ADMIN}/login`, 'data-link': '' },
      'Cerrar sesión',
    );
    salir.addEventListener('click', () => {
      borrarSesion();
    });
    const nav = el(
      'nav',
      { class: 'admin-nav', 'aria-label': 'Navegación del área administrativa' },
      ...enlaces,
      salir,
    );
    const cabecera = el(
      'header',
      { class: 'admin-header' },
      el('div', { class: 'admin-header__brand' }, 'Turnos odontológicos'),
    );
    const cuerpo = el('div', { class: 'admin-body' }, nav, contenido);
    montar(contenedor, el('div', { class: 'admin-shell' }, cabecera, cuerpo));
  };
}

/** Todas las rutas del area privada (relativas a la base '/admin'). */
export const rutasAdmin: TablaRutas = {
  '/login': login,
  '/agenda': conLayout('/agenda', agenda),
  '/configuracion': conLayout('/configuracion', configuracion),
  '/pacientes': conLayout('/pacientes', pacientes),
};
