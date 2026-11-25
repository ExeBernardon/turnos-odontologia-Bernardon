import type { HorarioAtencion } from '../../api/types';
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
import { actualizarHorario, crearHorario, eliminarHorario, listarHorarios } from './horarios.api';
import { agruparPorDia, DIAS, validarRango } from './horarios.logica';

/** Seccion independiente de la pantalla de configuracion: su change solo modifica este archivo. */
export function render(seccion: HTMLElement): void {
  void iniciar(seccion);
}

function descripcion(h: HorarioAtencion, nombreDia: string): string {
  return nombreDia + ' ' + h.horaInicio + ' a ' + h.horaFin;
}

/** Monta la seccion y resuelve cuando termina la carga inicial (usado por los tests). */
export async function iniciar(seccion: HTMLElement): Promise<void> {
  let items: HorarioAtencion[] = [];
  let editando: HorarioAtencion | null = null;
  let confirmando: number | null = null;
  let ocupado = false;

  const avisos = crearAvisos();
  const avisosForm = crearAvisos();
  const lista = el('div', { class: 'cfg-lista' });

  const selectorDia = el('select', { required: '' });
  DIAS.forEach((nombreDia, i) => selectorDia.append(el('option', { value: String(i + 1) }, nombreDia)));
  const dia = crearCampo('hor', 'diaSemana', 'Día', selectorDia);
  const inicio = crearCampo('hor', 'horaInicio', 'Hora de inicio', el('input', { type: 'time', required: '' }));
  const fin = crearCampo('hor', 'horaFin', 'Hora de fin', el('input', { type: 'time', required: '' }));
  const campos: Record<string, Campo> = { diaSemana: dia, horaInicio: inicio, horaFin: fin };
  const leyenda = el('legend', {}, 'Nuevo rango');
  const enviar = crearBoton('Agregar rango', { tipo: 'submit' });
  const cancelar = crearBoton('Cancelar edición');
  cancelar.hidden = true;
  const formulario = el(
    'form',
    { novalidate: '', class: 'cfg-form' },
    el(
      'fieldset',
      {},
      leyenda,
      dia.raiz,
      inicio.raiz,
      fin.raiz,
      avisosForm.raiz,
      el('div', { class: 'cfg-acciones' }, enviar, cancelar),
    ),
  );

  seccion.replaceChildren(el('h2', {}, 'Horarios de atención'), avisos.raiz, lista, formulario);

  function resetFormulario(): void {
    editando = null;
    dia.control.value = '1';
    inicio.control.value = '';
    fin.control.value = '';
    leyenda.textContent = 'Nuevo rango';
    enviar.textContent = 'Agregar rango';
    cancelar.hidden = true;
    limpiarErroresCampos(campos);
    avisosForm.limpiar();
  }

  function pintar(): void {
    const ul = el('ul', { class: 'cfg-dias' });
    for (const grupo of agruparPorDia(items)) {
      const contenido: Node[] = [el('h3', {}, grupo.nombre)];
      if (grupo.rangos.length === 0) {
        contenido.push(el('p', { class: 'cfg-vacio' }, 'Sin horario de atención'));
      } else {
        const rangos = el('ul', { class: 'cfg-rangos' });
        for (const r of grupo.rangos) {
          rangos.append(pintarRango(r, grupo.nombre));
        }
        contenido.push(rangos);
      }
      ul.append(el('li', { class: 'cfg-dia' }, ...contenido));
    }
    lista.replaceChildren(ul);
  }

  function pintarRango(r: HorarioAtencion, nombreDia: string): HTMLElement {
    const nombre = descripcion(r, nombreDia);
    const acciones = el('div', { class: 'cfg-acciones' });
    if (confirmando === r.id) {
      const confirmar = crearBoton('Confirmar', { etiqueta: 'Confirmar eliminar ' + nombre, clase: 'cfg-peligro' });
      const no = crearBoton('Cancelar', { etiqueta: 'Cancelar eliminar ' + nombre });
      confirmar.addEventListener('click', () => void eliminar(r));
      no.addEventListener('click', () => {
        confirmando = null;
        pintar();
        Array.from(lista.querySelectorAll('button'))
          .find((b) => b.getAttribute('aria-label') === 'Eliminar ' + nombre)
          ?.focus();
      });
      acciones.append(el('span', {}, '¿Eliminar este rango?'), confirmar, no);
      queueMicrotask(() => confirmar.focus());
    } else {
      const editar = crearBoton('Editar', { etiqueta: 'Editar ' + nombre });
      const borrar = crearBoton('Eliminar', { etiqueta: 'Eliminar ' + nombre, clase: 'cfg-peligro' });
      editar.addEventListener('click', () => empezarEdicion(r));
      borrar.addEventListener('click', () => {
        confirmando = r.id;
        pintar();
      });
      acciones.append(editar, borrar);
    }
    return el('li', { class: 'cfg-rango cfg-item' }, el('span', {}, r.horaInicio + ' a ' + r.horaFin), acciones);
  }

  function empezarEdicion(r: HorarioAtencion): void {
    editando = r;
    avisos.limpiar();
    avisosForm.limpiar();
    limpiarErroresCampos(campos);
    dia.control.value = String(r.diaSemana);
    inicio.control.value = r.horaInicio;
    fin.control.value = r.horaFin;
    leyenda.textContent = 'Editar rango';
    enviar.textContent = 'Guardar cambios';
    cancelar.hidden = false;
    dia.control.focus();
  }

  async function cargar(): Promise<boolean> {
    try {
      items = await listarHorarios();
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

  async function mutar(accion: () => Promise<unknown>, exito: string, alFallar: (error: unknown) => void): Promise<boolean> {
    if (ocupado) {
      return false;
    }
    ocupado = true;
    try {
      await accion();
    } catch (error) {
      ocupado = false;
      alFallar(error);
      return false;
    }
    ocupado = false;
    confirmando = null;
    if (await cargar()) {
      avisos.informar(exito);
    }
    return true;
  }

  async function eliminar(r: HorarioAtencion): Promise<void> {
    await mutar(
      () => eliminarHorario(r.id),
      'Rango eliminado.',
      (error) => {
        confirmando = null;
        pintar();
        avisos.fallar(mensajeDeError(error));
      },
    );
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    avisosForm.limpiar();
    const resultado = validarRango({
      diaSemana: dia.control.value,
      horaInicio: inicio.control.value,
      horaFin: fin.control.value,
    });
    if (!resultado.ok) {
      mostrarErroresCampos(campos, resultado.errores);
      return;
    }
    limpiarErroresCampos(campos);
    const valor = resultado.valor;
    const actual = editando;
    const peticion = actual === null ? () => crearHorario(valor) : () => actualizarHorario(actual.id, valor);
    void mutar(peticion, actual === null ? 'Rango agregado.' : 'Rango actualizado.', (error) =>
      mostrarErrorServidor(campos, avisosForm, error),
    ).then((ok) => {
      if (ok) {
        resetFormulario();
      }
    });
  });

  cancelar.addEventListener('click', () => {
    resetFormulario();
    dia.control.focus();
  });

  await cargar();
}
