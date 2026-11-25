import type { Paciente, Prestacion, Turno } from '../../api/types';
import { crearBuscadorPacientes } from '../../shared/buscador-pacientes';
import { el } from '../../shared/dom';
import { detallesPorCampo } from '../configuracion/errores';
import { type Campo, crearAvisos, crearCampo, limpiarErroresCampos, mostrarErroresCampos } from '../configuracion/formulario';
import { buscarPacientes } from '../pacientes.api';
import { formatearDni, NOMBRE_MAX, validarPaciente } from '../pacientes.logica';
import { crearTurnoManual, listarPrestacionesActivas } from './acciones.api';
import {
  accionDeErrorTurno,
  armarTurnoManual,
  type EntradaTurnoManual,
  type PacienteElegido,
  textoResultado,
  validarContacto,
} from './acciones.logica';
import { crearSelectorHorario } from './selector-horario';

export interface OpcionesAltaManual {
  readonly ahora: () => Date;
  /** Anuncia el resultado en la region de estado de la agenda. */
  readonly anunciar: (texto: string) => void;
  /** Se llama con el turno creado, luego de cerrar el panel. */
  readonly alCrear: (turno: Turno) => void | Promise<void>;
  /** Se llama al cerrar el panel (boton "Cerrar" o envio exitoso). */
  readonly alCerrar: () => void;
}

type Modo = 'existente' | 'nuevo';

/** Formulario de carga manual de un turno. Se monta dentro de `contenedor` y enfoca su titulo. */
export function montarAltaManual(contenedor: HTMLElement, opciones: OpcionesAltaManual): void {
  let modo: Modo = 'existente';
  let elegido: Paciente | null = null;
  let prestaciones: readonly Prestacion[] = [];

  const idTitulo = 'alta-turno-titulo';
  const titulo = el('h2', { id: idTitulo, tabindex: '-1' }, 'Nuevo turno');
  const avisos = crearAvisos();

  // --- paciente
  const radioExistente = el('input', { type: 'radio', name: 'alta-turno-modo', id: 'alta-turno-modo-existente', value: 'existente', checked: '' });
  const radioNuevo = el('input', { type: 'radio', name: 'alta-turno-modo', id: 'alta-turno-modo-nuevo', value: 'nuevo' });
  const modos = el(
    'fieldset',
    { class: 'aa-grupo' },
    el('legend', {}, 'Paciente'),
    el('div', { class: 'aa-opcion' }, radioExistente, el('label', { for: 'alta-turno-modo-existente' }, 'Paciente existente')),
    el('div', { class: 'aa-opcion' }, radioNuevo, el('label', { for: 'alta-turno-modo-nuevo' }, 'Paciente nuevo')),
  );

  const zonaExistente = el('div', { class: 'aa-zona' });
  const zonaNuevo = el('div', { class: 'aa-zona' });
  zonaNuevo.hidden = true;

  const nombre = crearCampo('alta-turno', 'nombre', 'Nombre', el('input', { type: 'text', maxlength: String(NOMBRE_MAX), autocomplete: 'off' }));
  const apellido = crearCampo('alta-turno', 'apellido', 'Apellido', el('input', { type: 'text', maxlength: String(NOMBRE_MAX), autocomplete: 'off' }));
  const dni = crearCampo('alta-turno', 'dni', 'DNI', el('input', { type: 'text', inputmode: 'numeric', autocomplete: 'off' }));
  zonaNuevo.append(nombre.raiz, apellido.raiz, dni.raiz);

  // --- turno
  const prestacion = crearCampo('alta-turno', 'prestacionId', 'Prestación', el('select', {}));
  const selector = crearSelectorHorario({
    prefijo: 'alta-turno-hor',
    prestacionId: null,
    ahora: opciones.ahora,
    alElegir: () => undefined,
  });
  const email = crearCampo('alta-turno', 'emailContacto', 'Email', el('input', { type: 'email', maxlength: '150', autocomplete: 'off' }));
  const telefono = crearCampo('alta-turno', 'telefonoContacto', 'Teléfono', el('input', { type: 'tel', maxlength: '30', autocomplete: 'off' }));
  const ayudaContacto = el('p', { class: 'aa-ayuda' });
  const cerrar = el('button', { type: 'button', class: 'aa-boton' }, 'Cerrar');
  const enviar = el('button', { type: 'submit', class: 'aa-boton aa-boton--primario' }, 'Cargar turno');

  const formulario = el(
    'form',
    { novalidate: '', class: 'aa-form' },
    modos,
    zonaExistente,
    zonaNuevo,
    prestacion.raiz,
    el('fieldset', { class: 'aa-grupo' }, el('legend', {}, 'Horario'), selector.raiz),
    ayudaContacto,
    email.raiz,
    telefono.raiz,
    avisos.raiz,
    el('div', { class: 'aa-acciones' }, enviar, cerrar),
  );
  const seccion = el('section', { class: 'aa-panel', 'aria-labelledby': idTitulo }, titulo, formulario);
  contenedor.replaceChildren(seccion);
  titulo.focus();

  // --- paciente existente
  const buscador = crearBuscadorPacientes({
    prefijo: 'alta-turno-pac',
    buscar: buscarPacientes,
    alSeleccionar: elegirPaciente,
    textoSeleccionar: 'Elegir',
  });

  function pintarExistente(): void {
    if (elegido === null) {
      zonaExistente.replaceChildren(buscador.raiz);
      return;
    }
    const cambiar = el('button', { type: 'button', class: 'aa-boton' }, 'Cambiar paciente');
    cambiar.addEventListener('click', () => {
      elegido = null;
      pintarExistente();
      buscador.enfocar();
    });
    zonaExistente.replaceChildren(
      el('p', { class: 'aa-paciente' }, el('strong', {}, `${elegido.apellido}, ${elegido.nombre}`), ` · DNI ${formatearDni(elegido.dni)}`),
      cambiar,
    );
  }

  function elegirPaciente(paciente: Paciente): void {
    elegido = paciente;
    pintarExistente();
    const contacto = paciente.ultimoContacto;
    email.control.value = contacto?.email ?? '';
    telefono.control.value = contacto?.telefono ?? '';
    ayudaContacto.textContent =
      contacto === null
        ? 'Este paciente no tiene turnos anteriores: ingresá su email y teléfono.'
        : 'Email y teléfono del último turno del paciente: podés editarlos.';
  }

  function cambiarModo(nuevo: Modo): void {
    modo = nuevo;
    zonaExistente.hidden = modo !== 'existente';
    zonaNuevo.hidden = modo !== 'nuevo';
  }
  radioExistente.addEventListener('change', () => cambiarModo('existente'));
  radioNuevo.addEventListener('change', () => cambiarModo('nuevo'));
  pintarExistente();

  // --- prestaciones
  function pintarPrestaciones(): void {
    const actual = prestacion.control.value;
    const opcionesSelect = [
      el('option', { value: '' }, 'Elegí una prestación'),
      ...prestaciones.map((p) => el('option', { value: String(p.id) }, `${p.nombre} (${p.duracionMinutos} min)`)),
    ];
    prestacion.control.replaceChildren(...opcionesSelect);
    prestacion.control.value = prestaciones.some((p) => String(p.id) === actual) ? actual : '';
  }

  async function cargarPrestaciones(): Promise<void> {
    try {
      prestaciones = await listarPrestacionesActivas();
      pintarPrestaciones();
    } catch {
      avisos.fallar('No se pudieron cargar las prestaciones.');
    }
  }
  void cargarPrestaciones();

  function prestacionElegida(): number | null {
    const valor = Number(prestacion.control.value);
    return prestacion.control.value === '' || Number.isNaN(valor) ? null : valor;
  }

  prestacion.control.addEventListener('change', () => {
    void selector.cambiarPrestacion(prestacionElegida());
  });

  // --- envio
  const campos: Record<string, Campo> = {
    nombre,
    apellido,
    dni,
    prestacionId: prestacion,
    emailContacto: email,
    telefonoContacto: telefono,
  };

  function cerrarPanel(): void {
    contenedor.replaceChildren();
    opciones.alCerrar();
  }
  cerrar.addEventListener('click', cerrarPanel);

  interface Lectura {
    readonly entrada: EntradaTurnoManual | null;
    readonly errores: Record<string, string>;
    readonly generales: readonly string[];
  }

  function leer(): Lectura {
    const errores: Record<string, string> = {};
    const generales: string[] = [];
    let pacienteElegido: PacienteElegido | null = null;
    if (modo === 'existente') {
      if (elegido === null) {
        generales.push('Elegí un paciente o cargá uno nuevo.');
      } else {
        pacienteElegido = { tipo: 'existente', paciente: elegido };
      }
    } else {
      const datos = { nombre: nombre.control.value, apellido: apellido.control.value, dni: dni.control.value };
      Object.assign(errores, validarPaciente(datos));
      pacienteElegido = { tipo: 'nuevo', datos };
    }
    const idPrestacion = prestacionElegida();
    if (idPrestacion === null) {
      errores['prestacionId'] = 'Elegí una prestación.';
    }
    const contacto = validarContacto(email.control.value, telefono.control.value);
    if (contacto.email !== undefined) {
      errores['emailContacto'] = contacto.email;
    }
    if (contacto.telefono !== undefined) {
      errores['telefonoContacto'] = contacto.telefono;
    }
    const inicio = selector.elegido();
    if (inicio === null) {
      generales.push('Elegí un horario libre.');
    }
    if (pacienteElegido === null || idPrestacion === null || inicio === null || Object.keys(errores).length > 0) {
      return { entrada: null, errores, generales };
    }
    return {
      entrada: {
        paciente: pacienteElegido,
        prestacionId: idPrestacion,
        inicio,
        emailContacto: email.control.value,
        telefonoContacto: telefono.control.value,
      },
      errores,
      generales,
    };
  }

  function manejarError(error: unknown): void {
    const { mensaje, efecto } = accionDeErrorTurno(error, 'alta');
    switch (efecto) {
      case 'refrescarHorarios':
        avisos.fallar(mensaje);
        void selector.refrescar();
        return;
      case 'recargarPrestaciones':
        avisos.fallar(mensaje);
        void cargarPrestaciones().then(() => selector.cambiarPrestacion(prestacionElegida()));
        return;
      case 'campoDni':
        if (modo === 'nuevo') {
          mostrarErroresCampos(campos, { dni: mensaje });
        } else {
          avisos.fallar(mensaje);
        }
        return;
      case 'campos': {
        const propios: Record<string, string> = {};
        for (const [campo, texto] of Object.entries(detallesPorCampo(error))) {
          const nombreCampo = campo.replace(/^paciente./, '');
          if (nombreCampo in campos) {
            propios[nombreCampo] = texto;
          }
        }
        if (Object.keys(propios).length > 0) {
          mostrarErroresCampos(campos, propios);
        } else {
          avisos.fallar(mensaje);
        }
        return;
      }
      default:
        if (mensaje !== '') {
          avisos.fallar(mensaje);
        }
    }
  }

  let enviando = false;

  async function enviarFormulario(): Promise<void> {
    if (enviando) {
      return;
    }
    avisos.limpiar();
    limpiarErroresCampos(campos);
    const { entrada, errores, generales } = leer();
    if (entrada === null) {
      mostrarErroresCampos(campos, errores);
      if (generales.length > 0) {
        avisos.fallar(generales.join(' '));
      }
      return;
    }
    enviando = true;
    enviar.disabled = true;
    formulario.setAttribute('aria-busy', 'true');
    try {
      const turno = await crearTurnoManual(armarTurnoManual(entrada));
      opciones.anunciar(textoResultado(turno, 'alta'));
      cerrarPanel();
      await opciones.alCrear(turno);
    } catch (error) {
      manejarError(error);
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
