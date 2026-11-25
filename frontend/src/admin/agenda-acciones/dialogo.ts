import { el } from '../../shared/dom';

export interface OpcionesDialogo {
  readonly titulo: string;
  readonly descripcion: string;
  /** Texto del boton que confirma la accion (ej. "Cancelar turno"). */
  readonly aceptar: string;
  /** Elemento que lo abrio: recupera el foco al cerrar. */
  readonly disparador: HTMLElement;
}

interface Abierto {
  readonly cerrar: (resultado: boolean) => void;
}

let abierto: Abierto | null = null;
let contador = 0;

/**
 * Dialogo de confirmacion modal (`alertdialog`). El foco arranca en "Volver" (la opcion segura), Tab queda
 * atrapado entre los botones, Escape cancela y el resto de la pagina queda `inert` mientras esta abierto.
 * Resuelve `true` solo si se acepta. No usa `<dialog>.showModal()` porque jsdom no lo implementa.
 */
export function confirmarAccion(opciones: OpcionesDialogo): Promise<boolean> {
  abierto?.cerrar(false);

  return new Promise<boolean>((resolver) => {
    contador += 1;
    const idTitulo = `aa-dialogo-titulo-${contador}`;
    const idTexto = `aa-dialogo-texto-${contador}`;
    const volver = el('button', { type: 'button', class: 'aa-boton' }, 'Volver');
    const aceptar = el('button', { type: 'button', class: 'aa-boton aa-boton--peligro' }, opciones.aceptar);
    const caja = el(
      'div',
      {
        class: 'aa-dialogo',
        role: 'alertdialog',
        'aria-modal': 'true',
        'aria-labelledby': idTitulo,
        'aria-describedby': idTexto,
      },
      el('h2', { id: idTitulo, class: 'aa-dialogo__titulo' }, opciones.titulo),
      el('p', { id: idTexto, class: 'aa-dialogo__texto' }, opciones.descripcion),
      el('div', { class: 'aa-dialogo__botones' }, volver, aceptar),
    );
    const fondo = el('div', { class: 'aa-dialogo-fondo' }, caja);

    const ocultados = Array.from(document.body.children).map((nodo) => ({
      nodo,
      ariaHidden: nodo.getAttribute('aria-hidden'),
      inerte: nodo.hasAttribute('inert'),
    }));
    for (const { nodo } of ocultados) {
      nodo.setAttribute('inert', '');
      nodo.setAttribute('aria-hidden', 'true');
    }
    document.body.append(fondo);

    function cerrar(resultado: boolean): void {
      if (abierto !== actual) {
        return;
      }
      abierto = null;
      fondo.remove();
      for (const { nodo, ariaHidden, inerte } of ocultados) {
        if (!inerte) {
          nodo.removeAttribute('inert');
        }
        if (ariaHidden === null) {
          nodo.removeAttribute('aria-hidden');
        } else {
          nodo.setAttribute('aria-hidden', ariaHidden);
        }
      }
      if (opciones.disparador.isConnected) {
        opciones.disparador.focus();
      }
      resolver(resultado);
    }

    const actual: Abierto = { cerrar };
    abierto = actual;

    volver.addEventListener('click', () => cerrar(false));
    aceptar.addEventListener('click', () => cerrar(true));
    caja.addEventListener('keydown', (evento) => {
      if (evento.key === 'Escape') {
        evento.preventDefault();
        cerrar(false);
      } else if (evento.key === 'Tab') {
        if (evento.shiftKey && document.activeElement === volver) {
          evento.preventDefault();
          aceptar.focus();
        } else if (!evento.shiftKey && document.activeElement === aceptar) {
          evento.preventDefault();
          volver.focus();
        }
      }
    });

    volver.focus();
  });
}
