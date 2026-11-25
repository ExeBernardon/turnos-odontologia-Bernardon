import { ErrorApi } from '../../api/cliente';
import type { Prestacion } from '../../api/types';
import { el } from '../../shared/dom';
import './configuracion.css';
import { mensajeDeError } from './errores';
import {
  type Campo,
  crearAvisos,
  crearBoton,
  crearCampo,
  limpiarErroresCampos,
  mostrarErroresCampos,
  mostrarErrorServidor,
} from './formulario';
import { actualizarPrestacion, crearPrestacion, eliminarPrestacion, listarPrestaciones } from './prestaciones.api';
import { DURACION_MAX, NOMBRE_MAX, validarPrestacion } from './prestaciones.logica';

/** Seccion independiente de la pantalla de configuracion: su change solo modifica este archivo. */
export function render(seccion: HTMLElement): void {
  void iniciar(seccion);
}

/** Monta la seccion y resuelve cuando termina la carga inicial (usado por los tests). */
export async function iniciar(seccion: HTMLElement): Promise<void> {
  let items: Prestacion[] = [];
  let editando: Prestacion | null = null;
  let confirmando: number | null = null;
  let ocupado = false;

  const avisos = crearAvisos();
  const lista = el('div', { class: 'cfg-lista' });

  const nombre = crearCampo(
    'prest',
    'nombre',
    'Nombre',
    el('input', { type: 'text', maxlength: String(NOMBRE_MAX), autocomplete: 'off', required: '' }),
  );
  const duracion = crearCampo(
    'prest',
    'duracionMinutos',
    'Duración (minutos)',
    el('input', { type: 'number', min: '1', max: String(DURACION_MAX), step: '1', inputmode: 'numeric', required: '' }),
  );
  const campos: Record<string, Campo> = { nombre, duracionMinutos: duracion };
  const leyenda = el('legend', {}, 'Nueva prestación');
  const enviar = crearBoton('Crear prestación', { tipo: 'submit' });
  const cancelar = crearBoton('Cancelar edición');
  cancelar.hidden = true;
  const formulario = el(
    'form',
    { novalidate: '', class: 'cfg-form' },
    el('fieldset', {}, leyenda, nombre.raiz, duracion.raiz, el('div', { class: 'cfg-acciones' }, enviar, cancelar)),
  );

  seccion.replaceChildren(el('h2', {}, 'Prestaciones'), avisos.raiz, lista, formulario);

  function resetFormulario(): void {
    editando = null;
    nombre.control.value = '';
    duracion.control.value = '';
    leyenda.textContent = 'Nueva prestación';
    enviar.textContent = 'Crear prestación';
    cancelar.hidden = true;
    limpiarErroresCampos(campos);
  }

  function pintar(): void {
    if (items.length === 0) {
      lista.replaceChildren(el('p', {}, 'No hay prestaciones cargadas.'));
      return;
    }
    const ul = el('ul', { class: 'cfg-items' });
    for (const p of items) {
      ul.append(pintarItem(p));
    }
    lista.replaceChildren(ul);
  }

  function pintarItem(p: Prestacion): HTMLElement {
    const info = el(
      'div',
      { class: 'cfg-item-info' },
      el('strong', {}, p.nombre),
      el('span', {}, ' ' + p.duracionMinutos + ' min '),
      el('span', { class: p.activa ? 'cfg-badge' : 'cfg-badge cfg-badge-inactiva' }, p.activa ? 'Activa' : 'Inactiva'),
    );
    const acciones = el('div', { class: 'cfg-acciones' });
    if (confirmando === p.id) {
      const confirmar = crearBoton('Confirmar', { etiqueta: 'Confirmar eliminar ' + p.nombre, clase: 'cfg-peligro' });
      const no = crearBoton('Cancelar', { etiqueta: 'Cancelar eliminar ' + p.nombre });
      confirmar.addEventListener('click', () => void eliminar(p));
      no.addEventListener('click', () => {
        confirmando = null;
        pintar();
        Array.from(lista.querySelectorAll('button'))
          .find((b) => b.getAttribute('aria-label') === 'Eliminar ' + p.nombre)
          ?.focus();
      });
      acciones.append(el('span', {}, '¿Eliminar «' + p.nombre + '»?'), confirmar, no);
      queueMicrotask(() => confirmar.focus());
    } else {
      const editar = crearBoton('Editar', { etiqueta: 'Editar ' + p.nombre });
      const alternar = crearBoton(p.activa ? 'Desactivar' : 'Activar', {
        etiqueta: (p.activa ? 'Desactivar ' : 'Activar ') + p.nombre,
      });
      const borrar = crearBoton('Eliminar', { etiqueta: 'Eliminar ' + p.nombre, clase: 'cfg-peligro' });
      editar.addEventListener('click', () => empezarEdicion(p));
      alternar.addEventListener('click', () => void cambiarActiva(p, !p.activa));
      borrar.addEventListener('click', () => {
        confirmando = p.id;
        pintar();
      });
      acciones.append(editar, alternar, borrar);
    }
    return el('li', { class: 'cfg-item' }, info, acciones);
  }

  function empezarEdicion(p: Prestacion): void {
    editando = p;
    avisos.limpiar();
    limpiarErroresCampos(campos);
    nombre.control.value = p.nombre;
    duracion.control.value = String(p.duracionMinutos);
    leyenda.textContent = 'Editar prestación';
    enviar.textContent = 'Guardar cambios';
    cancelar.hidden = false;
    nombre.control.focus();
  }

  async function cargar(): Promise<boolean> {
    try {
      items = await listarPrestaciones();
      avisos.limpiar();
      pintar();
      return true;
    } catch (error) {
      const reintentar = crearBoton('Reintentar');
      reintentar.addEventListener('click', () => void cargar());
      avisos.fallar(mensajeDeError(error), reintentar);
      return false;
    }
  }

  /** Ejecuta una mutacion; si sale bien recarga el listado, si no deja el estado como estaba. */
  async function mutar(accion: () => Promise<unknown>, exito: string, alFallar?: (error: unknown) => void): Promise<boolean> {
    if (ocupado) {
      return false;
    }
    ocupado = true;
    try {
      await accion();
    } catch (error) {
      ocupado = false;
      if (alFallar === undefined) {
        avisos.fallar(mensajeDeError(error));
      } else {
        alFallar(error);
      }
      return false;
    }
    ocupado = false;
    confirmando = null;
    if (await cargar()) {
      avisos.informar(exito);
    }
    return true;
  }

  async function cambiarActiva(p: Prestacion, activa: boolean): Promise<void> {
    await mutar(
      () => actualizarPrestacion(p.id, { nombre: p.nombre, duracionMinutos: p.duracionMinutos, activa }),
      activa ? 'Prestación activada.' : 'Prestación desactivada.',
    );
  }

  async function eliminar(p: Prestacion): Promise<void> {
    await mutar(
      () => eliminarPrestacion(p.id),
      'Prestación eliminada.',
      (error) => {
        confirmando = null;
        pintar();
        let accion: HTMLButtonElement | undefined;
        if (p.activa && error instanceof ErrorApi && error.codigo === 'PRESTACION_CON_TURNOS') {
          accion = crearBoton('Desactivar en su lugar');
          accion.addEventListener('click', () => void cambiarActiva(p, false));
        }
        avisos.fallar(mensajeDeError(error), accion);
      },
    );
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const resultado = validarPrestacion({ nombre: nombre.control.value, duracion: duracion.control.value });
    if (!resultado.ok) {
      avisos.limpiar();
      mostrarErroresCampos(campos, resultado.errores);
      return;
    }
    limpiarErroresCampos(campos);
    const { nombre: n, duracionMinutos } = resultado.valor;
    const actual = editando;
    const peticion = actual === null
      ? () => crearPrestacion({ nombre: n, duracionMinutos, activa: true })
      : () => actualizarPrestacion(actual.id, { nombre: n, duracionMinutos, activa: actual.activa });
    void mutar(peticion, actual === null ? 'Prestación creada.' : 'Prestación actualizada.', (error) =>
      mostrarErrorServidor(campos, avisos, error),
    ).then((ok) => {
      if (ok) {
        resetFormulario();
      }
    });
  });

  cancelar.addEventListener('click', () => {
    resetFormulario();
    nombre.control.focus();
  });

  await cargar();
}
