import { vi } from 'vitest';

export interface Llamada {
  readonly metodo: string;
  readonly url: string;
  readonly body: unknown;
}

type Manejador = (llamada: Llamada) => Response | Promise<Response>;

export function respuesta(status: number, cuerpo?: unknown): Response {
  return new Response(cuerpo === undefined ? null : JSON.stringify(cuerpo), { status });
}

/**
 * Reemplaza `fetch` por un servidor falso: las rutas son "METODO /url". Una lista de manejadores
 * se consume en orden (el ultimo se repite), para simular el listado antes y despues de una mutacion.
 */
export function stubServidor(rutas: Record<string, Manejador | Manejador[]>): { llamadas: Llamada[] } {
  const llamadas: Llamada[] = [];
  const indices = new Map<string, number>();
  vi.stubGlobal(
    'fetch',
    vi.fn(async (entrada: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
      const url = typeof entrada === 'string' ? entrada : entrada instanceof URL ? entrada.pathname : entrada.url;
      const metodo = init?.method ?? 'GET';
      const cuerpo: unknown = typeof init?.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined;
      const llamada = { metodo, url, body: cuerpo };
      llamadas.push(llamada);
      const clave = metodo + ' ' + url;
      const entradaRuta = rutas[clave];
      if (entradaRuta === undefined) {
        throw new Error('ruta sin manejador: ' + clave);
      }
      if (Array.isArray(entradaRuta)) {
        const i = indices.get(clave) ?? 0;
        indices.set(clave, i + 1);
        const m = entradaRuta[Math.min(i, entradaRuta.length - 1)];
        if (m === undefined) {
          throw new Error('sin manejadores: ' + clave);
        }
        return m(llamada);
      }
      return entradaRuta(llamada);
    }),
  );
  return { llamadas };
}

/** Busca un boton por su nombre accesible (aria-label o texto). */
export function boton(raiz: ParentNode, nombre: string): HTMLButtonElement {
  const b = Array.from(raiz.querySelectorAll('button')).find((x) => (x.getAttribute('aria-label') ?? x.textContent) === nombre);
  if (b === undefined) {
    throw new Error('boton no encontrado: ' + nombre);
  }
  return b;
}

/** Campo de formulario por el texto de su label. */
export function campoPorLabel(raiz: ParentNode, texto: string): HTMLInputElement | HTMLSelectElement {
  const label = Array.from(raiz.querySelectorAll('label')).find((l) => l.textContent === texto);
  const control = label?.control;
  if (!(control instanceof HTMLInputElement) && !(control instanceof HTMLSelectElement)) {
    throw new Error('campo no encontrado: ' + texto);
  }
  return control;
}
