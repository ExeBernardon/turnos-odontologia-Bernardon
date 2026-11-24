import { el, montar } from './dom';

export interface ContextoRuta {
  /** Ruta relativa al area (sin la base), por ejemplo `/turno/cancelar`. */
  readonly ruta: string;
  readonly query: URLSearchParams;
}

/** Cada pantalla exporta una funcion con esta firma. */
export type Pantalla = (contenedor: HTMLElement, ctx: ContextoRuta) => void;

export type TablaRutas = Readonly<Record<string, Pantalla>>;

export interface ConfigRouter {
  /** Prefijo del area: `''` (publica) o `'/admin'`. */
  readonly base: string;
  /** Ruta (relativa al area) a la que redirige la raiz del area. */
  readonly raiz: string;
  readonly rutas: TablaRutas;
  readonly contenedor: HTMLElement;
}

export interface Router {
  iniciar(): void;
  detener(): void;
  /** Navega a una ruta relativa al area (admite query) sin recargar el documento. */
  navegar(destino: string): void;
}

export function crearRouter(config: ConfigRouter): Router {
  const { base, raiz, rutas, contenedor } = config;

  const relativa = (pathname: string): string => {
    const sinBase = base !== '' && pathname.startsWith(base) ? pathname.slice(base.length) : pathname;
    const limpia = sinBase.length > 1 && sinBase.endsWith('/') ? sinBase.slice(0, -1) : sinBase;
    return limpia === '' ? '/' : limpia;
  };

  const resolver = (): void => {
    const ruta = relativa(window.location.pathname);
    if (ruta === '/') {
      window.history.replaceState({}, '', base + raiz);
      resolver();
      return;
    }
    const ctx: ContextoRuta = { ruta, query: new URLSearchParams(window.location.search) };
    const pantalla = rutas[ruta];
    if (pantalla === undefined) {
      montar(contenedor, el('h1', {}, 'Página no encontrada'), el('p', {}, 'La dirección que buscás no existe.'));
      return;
    }
    pantalla(contenedor, ctx);
  };

  const alClick = (evento: MouseEvent): void => {
    if (evento.defaultPrevented || evento.button !== 0 || evento.metaKey || evento.ctrlKey || evento.shiftKey) {
      return;
    }
    const objetivo = evento.target instanceof Element ? evento.target.closest('a[data-link]') : null;
    const href = objetivo?.getAttribute('href');
    if (href === null || href === undefined || !href.startsWith('/')) {
      return;
    }
    evento.preventDefault();
    window.history.pushState({}, '', href);
    resolver();
  };

  return {
    iniciar() {
      document.addEventListener('click', alClick);
      window.addEventListener('popstate', resolver);
      resolver();
    },
    detener() {
      document.removeEventListener('click', alClick);
      window.removeEventListener('popstate', resolver);
    },
    navegar(destino) {
      window.history.pushState({}, '', base + destino);
      resolver();
    },
  };
}
