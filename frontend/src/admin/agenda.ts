import { placeholder } from '../shared/dom';
import type { ContextoRuta } from '../shared/router';

/** C-1x: reemplazar el contenido de esta pantalla (el router y la ruta ya estan registrados). */
export function render(contenedor: HTMLElement, _ctx: ContextoRuta): void {
  placeholder(contenedor, 'Agenda');
}
