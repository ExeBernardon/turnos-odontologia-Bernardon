import { el, montar } from '../../shared/dom';

/** Seccion independiente de la pantalla de configuracion: su change solo modifica este archivo. */
export function render(seccion: HTMLElement): void {
  montar(seccion, el('h2', {}, 'Prestaciones'), el('p', {}, 'Sección en construcción.'));
}
