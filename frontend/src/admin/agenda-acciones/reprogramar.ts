import type { Turno } from '../../api/types';
import { el } from '../../shared/dom';
import { crearAvisos } from '../configuracion/formulario';
import { reprogramarTurno } from './acciones.api';
import { accionDeErrorTurno, describirHorario, textoResultado } from './acciones.logica';
import { crearSelectorHorario } from './selector-horario';

export interface OpcionesReprogramar {
  readonly turno: Turno;
  readonly ahora: () => Date;
  /** Anuncia el resultado en la region de estado de la agenda. */
  readonly anunciar: (texto: string) => void;
  /** Muestra un error en la region de alerta de la agenda (el panel ya se cerro). */
  readonly alertar: (texto: string) => void;
  /** Pide recargar la agenda (tras un exito o cuando el turno cambio en el servidor). */
  readonly alCambiar: () => void | Promise<void>;
  /** Se llama al cerrar el panel, por cualquier motivo. */
  readonly alCerrar: () => void;
}

/** Formulario de reprogramacion de un turno. Se monta dentro de `contenedor` y enfoca su titulo. */
export function montarReprogramar(contenedor: HTMLElement, opciones: OpcionesReprogramar): void {
  const { turno } = opciones;
  const idTitulo = 'reprogramar-titulo';
  const titulo = el('h2', { id: idTitulo, tabindex: '-1' }, 'Reprogramar turno');
  const avisos = crearAvisos();
  const enviar = el('button', { type: 'submit', class: 'aa-boton aa-boton--primario' }, 'Reprogramar');
  const cerrar = el('button', { type: 'button', class: 'aa-boton' }, 'Cerrar');

  const selector = crearSelectorHorario({
    prefijo: 'reprogramar-hor',
    prestacionId: turno.prestacion.id,
    ahora: opciones.ahora,
    alElegir: (instante) => {
      enviar.textContent = instante === null ? 'Reprogramar' : `Reprogramar al ${describirHorario(instante)}`;
    },
  });

  const formulario = el(
    'form',
    { novalidate: '', class: 'aa-form' },
    el('fieldset', { class: 'aa-grupo' }, el('legend', {}, 'Nuevo horario'), selector.raiz),
    avisos.raiz,
    el('div', { class: 'aa-acciones' }, enviar, cerrar),
  );
  const seccion = el(
    'section',
    { class: 'aa-panel', 'aria-labelledby': idTitulo },
    titulo,
    el(
      'p',
      { class: 'aa-resumen' },
      `${turno.paciente.nombre} ${turno.paciente.apellido} · ${turno.prestacion.nombre} · Horario actual: ${describirHorario(turno.inicio)}`,
    ),
    formulario,
  );
  contenedor.replaceChildren(seccion);
  titulo.focus();

  function cerrarPanel(): void {
    contenedor.replaceChildren();
    opciones.alCerrar();
  }
  cerrar.addEventListener('click', cerrarPanel);

  let enviando = false;

  async function enviarFormulario(): Promise<void> {
    if (enviando) {
      return;
    }
    avisos.limpiar();
    const inicio = selector.elegido();
    if (inicio === null) {
      avisos.fallar('Elegí un horario libre.');
      return;
    }
    enviando = true;
    enviar.disabled = true;
    formulario.setAttribute('aria-busy', 'true');
    try {
      const reprogramado = await reprogramarTurno(turno.id, inicio);
      opciones.anunciar(textoResultado(reprogramado, 'reprogramar'));
      cerrarPanel();
      await opciones.alCambiar();
    } catch (error) {
      const { mensaje, efecto } = accionDeErrorTurno(error, 'reprogramar');
      if (efecto === 'recargarAgenda') {
        cerrarPanel();
        opciones.alertar(mensaje);
        await opciones.alCambiar();
      } else if (efecto === 'refrescarHorarios') {
        avisos.fallar(mensaje);
        void selector.refrescar();
      } else if (mensaje !== '') {
        avisos.fallar(mensaje);
      }
    } finally {
      enviando = false;
      enviar.disabled = false;
      formulario.removeAttribute('aria-busy');
    }
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void enviarFormulario();
  });
}
