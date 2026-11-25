import type { ContextoRuta } from '../shared/router';
import { iniciarTurnoToken } from './turno-token';

export function render(contenedor: HTMLElement, ctx: ContextoRuta): void {
  void iniciarTurnoToken(contenedor, 'confirmar', ctx);
}
