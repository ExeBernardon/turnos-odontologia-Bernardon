import { el, montar } from '../../shared/dom';
import type { ContextoRuta } from '../../shared/router';
import { render as renderBloqueos } from './bloqueos';
import { render as renderHorarios } from './horarios';
import { render as renderPrestaciones } from './prestaciones';

export function render(contenedor: HTMLElement, _ctx: ContextoRuta): void {
  const prestaciones = el('section', { 'aria-labelledby': 'cfg-prestaciones' });
  const horarios = el('section', { 'aria-labelledby': 'cfg-horarios' });
  const bloqueos = el('section', { 'aria-labelledby': 'cfg-bloqueos' });
  montar(contenedor, el('h1', {}, 'Configuración'), prestaciones, horarios, bloqueos);
  renderPrestaciones(prestaciones);
  renderHorarios(horarios);
  renderBloqueos(bloqueos);
  prestaciones.querySelector('h2')?.setAttribute('id', 'cfg-prestaciones');
  horarios.querySelector('h2')?.setAttribute('id', 'cfg-horarios');
  bloqueos.querySelector('h2')?.setAttribute('id', 'cfg-bloqueos');
}
