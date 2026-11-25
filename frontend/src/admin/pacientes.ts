import { ErrorApi } from '../api/cliente';
import type { Paciente } from '../api/types';
import { crearBuscadorPacientes } from '../shared/buscador-pacientes';
import { el } from '../shared/dom';
import type { ContextoRuta } from '../shared/router';
import './configuracion/configuracion.css';
import {
  type Campo,
  crearAvisos,
  crearBoton,
  crearCampo,
  limpiarErroresCampos,
  mostrarErroresCampos,
  mostrarErrorServidor,
} from './configuracion/formulario';
import { mensajeDeError } from './configuracion/errores';
import { actualizarPaciente, buscarPacientes, crearPaciente, obtenerPaciente } from './pacientes.api';
import './pacientes.css';
import { NOMBRE_MAX, armarRequest, formatearDni, validarPaciente } from './pacientes.logica';

const MENSAJE_DNI_DUPLICADO = 'Ya existe un paciente con ese DNI.';
const MENSAJE_NO_EXISTE = 'El paciente ya no existe. Se actualizó la lista.';

export function render(contenedor: HTMLElement, _ctx: ContextoRuta): void {
  void iniciar(contenedor);
}

/** Monta la pantalla y resuelve cuando termina la carga inicial (usado por los tests). */
export async function iniciar(contenedor: HTMLElement): Promise<void> {
  let editando: Paciente | null = null;
  let origenFormulario: HTMLElement | null = null;
  let ocupado = false;

  const avisos = crearAvisos();
  const nuevo = crearBoton('Nuevo paciente');
  const buscador = crearBuscadorPacientes({
    prefijo: 'pac-buscador',
    buscar: buscarPacientes,
    alSeleccionar: (p) => void ver(p.id),
  });

  const detalle = el('section', { class: 'pac-detalle', 'aria-labelledby': 'pac-detalle-titulo' });
  detalle.hidden = true;

  const nombre = crearCampo(
    'pac',
    'nombre',
    'Nombre',
    el('input', { type: 'text', maxlength: String(NOMBRE_MAX), autocomplete: 'off', required: '' }),
  );
  const apellido = crearCampo(
    'pac',
    'apellido',
    'Apellido',
    el('input', { type: 'text', maxlength: String(NOMBRE_MAX), autocomplete: 'off', required: '' }),
  );
  const dni = crearCampo('pac', 'dni', 'DNI', el('input', { type: 'text', inputmode: 'numeric', autocomplete: 'off', required: '' }));
  const campos: Record<string, Campo> = { nombre, apellido, dni };
  const leyenda = el('legend', {}, 'Nuevo paciente');
  const enviar = crearBoton('Crear paciente', { tipo: 'submit' });
  const cancelar = crearBoton('Cancelar');
  const formulario = el(
    'form',
    { novalidate: '', class: 'cfg-form pac-form' },
    el('fieldset', {}, leyenda, nombre.raiz, apellido.raiz, dni.raiz, el('div', { class: 'cfg-acciones' }, enviar, cancelar)),
  );
  formulario.hidden = true;

  contenedor.replaceChildren(
    el('h1', {}, 'Pacientes'),
    avisos.raiz,
    el('div', { class: 'pac-acciones' }, nuevo),
    buscador.raiz,
    detalle,
    formulario,
  );

  function cerrarFormulario(): void {
    formulario.hidden = true;
    editando = null;
    limpiarErroresCampos(campos);
  }

  function cerrarDetalle(): void {
    detalle.hidden = true;
    detalle.replaceChildren();
  }

  function fila(termino: string, valor: string): Node[] {
    return [el('dt', {}, termino), el('dd', {}, valor)];
  }

  function mostrarDetalle(paciente: Paciente, enfocar: boolean): void {
    const titulo = el('h2', { id: 'pac-detalle-titulo', tabindex: '-1' }, paciente.apellido + ', ' + paciente.nombre);
    const contacto = paciente.ultimoContacto;
    const datos = el(
      'dl',
      { class: 'pac-datos' },
      ...fila('Nombre', paciente.nombre),
      ...fila('Apellido', paciente.apellido),
      ...fila('DNI', formatearDni(paciente.dni)),
      ...(contacto === null
        ? fila('Contacto', 'Sin turnos registrados: todavía no hay datos de contacto')
        : [...fila('Email', contacto.email), ...fila('Teléfono', contacto.telefono)]),
    );
    const editar = crearBoton('Editar');
    editar.addEventListener('click', () => abrirFormulario(paciente, editar));
    const nota = contacto === null ? [] : [el('p', { class: 'cfg-ayuda' }, 'Contacto del último turno (solo lectura)')];
    detalle.replaceChildren(titulo, datos, ...nota, editar);
    detalle.hidden = false;
    if (enfocar) {
      titulo.focus();
    }
  }

  function abrirFormulario(paciente: Paciente | null, origen: HTMLElement): void {
    editando = paciente;
    origenFormulario = origen;
    avisos.limpiar();
    limpiarErroresCampos(campos);
    nombre.control.value = paciente?.nombre ?? '';
    apellido.control.value = paciente?.apellido ?? '';
    dni.control.value = paciente?.dni ?? '';
    leyenda.textContent = paciente === null ? 'Nuevo paciente' : 'Editar paciente';
    enviar.textContent = paciente === null ? 'Crear paciente' : 'Guardar cambios';
    formulario.hidden = false;
    nombre.control.focus();
  }

  /** El paciente ya no existe: se avisa, se cierra todo y se recarga la busqueda vigente. */
  async function manejarInexistente(): Promise<void> {
    cerrarFormulario();
    cerrarDetalle();
    avisos.fallar(MENSAJE_NO_EXISTE);
    await buscador.buscar();
  }

  function esNoEncontrado(error: unknown): boolean {
    return error instanceof ErrorApi && error.codigo === 'RECURSO_NO_ENCONTRADO';
  }

  async function ver(id: number): Promise<void> {
    avisos.limpiar();
    cerrarFormulario();
    try {
      mostrarDetalle(await obtenerPaciente(id), true);
    } catch (error) {
      if (esNoEncontrado(error)) {
        await manejarInexistente();
      } else {
        avisos.fallar(mensajeDeError(error));
      }
    }
  }

  function mostrarErrorGuardado(error: unknown): void {
    if (error instanceof ErrorApi && error.codigo === 'DNI_DUPLICADO') {
      mostrarErroresCampos(campos, { dni: MENSAJE_DNI_DUPLICADO });
      return;
    }
    mostrarErrorServidor(campos, avisos, error);
  }

  async function guardar(): Promise<void> {
    const entrada = { nombre: nombre.control.value, apellido: apellido.control.value, dni: dni.control.value };
    const errores = validarPaciente(entrada);
    if (Object.keys(errores).length > 0) {
      avisos.limpiar();
      mostrarErroresCampos(campos, errores);
      return;
    }
    if (ocupado) {
      return;
    }
    limpiarErroresCampos(campos);
    const body = armarRequest(entrada);
    const actual = editando;
    ocupado = true;
    try {
      const guardado = actual === null ? await crearPaciente(body) : await actualizarPaciente(actual.id, body);
      ocupado = false;
      cerrarFormulario();
      avisos.informar(actual === null ? 'Paciente creado' : 'Cambios guardados');
      mostrarDetalle(guardado, true);
      await buscador.buscar();
    } catch (error) {
      ocupado = false;
      if (esNoEncontrado(error)) {
        await manejarInexistente();
      } else {
        mostrarErrorGuardado(error);
      }
    }
  }

  nuevo.addEventListener('click', () => abrirFormulario(null, nuevo));

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void guardar();
  });

  cancelar.addEventListener('click', () => {
    cerrarFormulario();
    const origen = origenFormulario;
    origenFormulario = null;
    if (origen !== null && origen.isConnected) {
      origen.focus();
    }
  });

  await buscador.buscar('');
}
