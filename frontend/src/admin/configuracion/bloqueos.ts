import { ErrorApi } from '../../api/cliente';
import type { Bloqueo, BloqueoRequest, EstadoTurno, Turno } from '../../api/types';
import { el } from '../../shared/dom';
import { crearBloqueo, eliminarBloqueo, listarBloqueos, previsualizarBloqueo, turnosAfectadosDe } from './bloqueos.api';
import { MOTIVO_MAX, type Rango, rangoPorDefecto, validarBloqueo, validarRango } from './bloqueos.logica';
import './configuracion.css';
import { mensajeDeError } from './errores';
import { formatear } from './fechas';
import {
  type Campo,
  crearAvisos,
  crearBoton,
  crearCampo,
  limpiarErroresCampos,
  mostrarErroresCampos,
  mostrarErrorServidor,
} from './formulario';

const ESTADOS: Readonly<Record<EstadoTurno, string>> = {
  PENDIENTE: 'pendiente',
  CONFIRMADO: 'confirmado',
  CANCELADO: 'cancelado',
  ASISTIO: 'asistió',
  AUSENTE: 'ausente',
};

function plural(n: number, singular: string, pluralTexto: string): string {
  return `${n} ${n === 1 ? singular : pluralTexto}`;
}

/** Seccion independiente de la pantalla de configuracion: su change solo modifica este archivo. */
export function render(seccion: HTMLElement): void {
  void iniciar(seccion);
}

/** Monta la seccion y resuelve cuando termina la carga inicial (usado por los tests). */
export async function iniciar(seccion: HTMLElement): Promise<void> {
  let items: Bloqueo[] = [];
  let rango: Rango = rangoPorDefecto();
  let confirmando: number | null = null;
  let ocupado = false;

  const avisos = crearAvisos();
  const lista = el('div', { class: 'cfg-lista' });
  const confirmacion = el('div', { class: 'cfg-confirmacion' });

  // Filtro de rango (listado)
  const desde = crearCampo('bloq-filtro', 'desde', 'Desde', el('input', { type: 'date', required: '' }));
  const hasta = crearCampo('bloq-filtro', 'hasta', 'Hasta', el('input', { type: 'date', required: '' }));
  desde.control.value = rango.desde;
  hasta.control.value = rango.hasta;
  const camposFiltro: Record<string, Campo> = { desde, hasta };
  const filtro = el(
    'form',
    { id: 'bloq-filtro', novalidate: '', class: 'cfg-form cfg-filtro' },
    el(
      'fieldset',
      {},
      el('legend', {}, 'Rango a mostrar'),
      el('div', { class: 'cfg-fila' }, desde.raiz, hasta.raiz),
      el('div', { class: 'cfg-acciones' }, crearBoton('Ver bloqueos', { tipo: 'submit' })),
    ),
  );

  // Alta
  const inicio = crearCampo('bloq', 'inicio', 'Inicio', el('input', { type: 'datetime-local', required: '' }));
  const fin = crearCampo('bloq', 'fin', 'Fin', el('input', { type: 'datetime-local', required: '' }));
  const motivo = crearCampo(
    'bloq',
    'motivo',
    'Motivo (opcional)',
    el('input', { type: 'text', maxlength: String(MOTIVO_MAX), autocomplete: 'off' }),
  );
  const campos: Record<string, Campo> = { inicio, fin, motivo };
  const formulario = el(
    'form',
    { id: 'bloq-alta', novalidate: '', class: 'cfg-form' },
    el(
      'fieldset',
      {},
      el('legend', {}, 'Nuevo bloqueo'),
      el('p', { class: 'cfg-ayuda' }, 'Las fechas y horas son de Buenos Aires (Argentina).'),
      el('div', { class: 'cfg-fila' }, inicio.raiz, fin.raiz),
      motivo.raiz,
      el('div', { class: 'cfg-acciones' }, crearBoton('Crear bloqueo', { tipo: 'submit' })),
    ),
  );

  seccion.replaceChildren(el('h2', {}, 'Bloqueos'), avisos.raiz, filtro, lista, formulario, confirmacion);

  function resetFormulario(): void {
    inicio.control.value = '';
    fin.control.value = '';
    motivo.control.value = '';
    limpiarErroresCampos(campos);
  }

  function cerrarConfirmacion(): void {
    confirmacion.replaceChildren();
  }

  function pintar(): void {
    if (items.length === 0) {
      lista.replaceChildren(el('p', { class: 'cfg-vacio' }, 'No hay bloqueos en el rango seleccionado.'));
      return;
    }
    const ul = el('ul', { class: 'cfg-items' });
    for (const b of items) {
      ul.append(pintarItem(b));
    }
    lista.replaceChildren(ul);
  }

  function pintarItem(b: Bloqueo): HTMLElement {
    const nombre = 'bloqueo del ' + formatear(b.inicio);
    const info = el(
      'div',
      { class: 'cfg-item-info' },
      el('strong', {}, formatear(b.inicio) + ' a ' + formatear(b.fin)),
      el('span', {}, ' · ' + (b.motivo === null || b.motivo === '' ? 'Sin motivo' : b.motivo)),
    );
    const acciones = el('div', { class: 'cfg-acciones' });
    if (confirmando === b.id) {
      const confirmar = crearBoton('Confirmar', { etiqueta: 'Confirmar eliminar ' + nombre, clase: 'cfg-peligro' });
      const no = crearBoton('Cancelar', { etiqueta: 'Cancelar eliminar ' + nombre });
      confirmar.addEventListener('click', () => void eliminar(b));
      no.addEventListener('click', () => {
        confirmando = null;
        pintar();
        Array.from(lista.querySelectorAll('button'))
          .find((x) => x.getAttribute('aria-label') === 'Eliminar ' + nombre)
          ?.focus();
      });
      acciones.append(
        el('span', {}, '¿Eliminar el bloqueo? No restituye los turnos que ya se cancelaron.'),
        confirmar,
        no,
      );
      queueMicrotask(() => confirmar.focus());
    } else {
      const borrar = crearBoton('Eliminar', { etiqueta: 'Eliminar ' + nombre, clase: 'cfg-peligro' });
      borrar.addEventListener('click', () => {
        confirmando = b.id;
        pintar();
      });
      acciones.append(borrar);
    }
    return el('li', { class: 'cfg-item' }, info, acciones);
  }

  function pintarTurno(t: Turno): HTMLElement {
    return el(
      'li',
      {},
      `${formatear(t.inicio)} · ${t.paciente.nombre} ${t.paciente.apellido} · ${t.prestacion.nombre} (${ESTADOS[t.estado]})`,
    );
  }

  /** Paso 2: lista de turnos afectados con aviso; solo crea si el odontologo confirma. */
  function mostrarConfirmacion(valor: BloqueoRequest, turnos: readonly Turno[]): void {
    const titulo = el('h3', { id: 'bloq-confirmacion-titulo', tabindex: '-1' }, 'Turnos afectados');
    const confirmar = crearBoton('Confirmar y cancelar turnos', { clase: 'cfg-peligro' });
    const volver = crearBoton('Volver');
    confirmar.addEventListener('click', () => void crear(valor, true));
    volver.addEventListener('click', () => {
      cerrarConfirmacion();
      avisos.limpiar();
      inicio.control.focus();
    });
    const panel = el(
      'div',
      { class: 'cfg-aviso-turnos', role: 'group', 'aria-labelledby': 'bloq-confirmacion-titulo', 'data-confirmacion': '' },
      titulo,
      el(
        'p',
        {},
        `Se cancelarán ${plural(turnos.length, 'turno', 'turnos')} para crear este bloqueo. Esta acción no se puede deshacer.`,
      ),
      el('ul', { class: 'cfg-items' }, ...turnos.map(pintarTurno)),
      el('div', { class: 'cfg-acciones' }, confirmar, volver),
    );
    confirmacion.replaceChildren(panel);
    avisos.limpiar();
    titulo.focus();
  }

  async function cargar(): Promise<boolean> {
    try {
      items = await listarBloqueos(rango);
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

  async function eliminar(b: Bloqueo): Promise<void> {
    if (ocupado) {
      return;
    }
    ocupado = true;
    try {
      await eliminarBloqueo(b.id);
    } catch (error) {
      ocupado = false;
      confirmando = null;
      if (error instanceof ErrorApi && error.codigo === 'RECURSO_NO_ENCONTRADO') {
        await cargar();
      } else {
        pintar();
      }
      avisos.fallar(mensajeDeError(error));
      return;
    }
    ocupado = false;
    confirmando = null;
    if (await cargar()) {
      avisos.informar('Bloqueo eliminado.');
    }
  }

  /** Crea el bloqueo; un 409 con turnos nuevos vuelve a pedir confirmacion con la lista actualizada. */
  async function crear(valor: BloqueoRequest, cancelarTurnosAfectados: boolean): Promise<void> {
    if (ocupado) {
      return;
    }
    ocupado = true;
    try {
      const creado = await crearBloqueo({ ...valor, cancelarTurnosAfectados });
      ocupado = false;
      cerrarConfirmacion();
      resetFormulario();
      const cancelados = creado.turnosCancelados.length;
      const exito = cancelados === 0
        ? 'Bloqueo creado.'
        : `Bloqueo creado. Se ${cancelados === 1 ? 'canceló' : 'cancelaron'} ${plural(cancelados, 'turno', 'turnos')}.`;
      if (await cargar()) {
        avisos.informar(exito);
      }
    } catch (error) {
      ocupado = false;
      const afectados = turnosAfectadosDe(error);
      if (afectados !== null) {
        mostrarConfirmacion(valor, afectados);
      } else {
        cerrarConfirmacion();
        mostrarErrorServidor(campos, avisos, error);
      }
    }
  }

  /** Paso 1: previsualiza; sin turnos afectados crea directo. */
  async function intentar(valor: BloqueoRequest): Promise<void> {
    if (ocupado) {
      return;
    }
    ocupado = true;
    let turnos: Turno[];
    try {
      turnos = await previsualizarBloqueo(valor);
    } catch (error) {
      ocupado = false;
      cerrarConfirmacion();
      mostrarErrorServidor(campos, avisos, error);
      return;
    }
    ocupado = false;
    if (turnos.length > 0) {
      mostrarConfirmacion(valor, turnos);
    } else {
      cerrarConfirmacion();
      await crear(valor, false);
    }
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const resultado = validarBloqueo({
      inicio: inicio.control.value,
      fin: fin.control.value,
      motivo: motivo.control.value,
    });
    if (!resultado.ok) {
      avisos.limpiar();
      cerrarConfirmacion();
      mostrarErroresCampos(campos, resultado.errores);
      return;
    }
    limpiarErroresCampos(campos);
    void intentar(resultado.valor);
  });

  filtro.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const resultado = validarRango({ desde: desde.control.value, hasta: hasta.control.value });
    if (!resultado.ok) {
      mostrarErroresCampos(camposFiltro, resultado.errores);
      return;
    }
    limpiarErroresCampos(camposFiltro);
    rango = resultado.valor;
    confirmando = null;
    void cargar();
  });

  await cargar();
}
