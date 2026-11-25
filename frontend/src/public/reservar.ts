import type { PrestacionPublica, TurnoResumenPublico } from '../api/types';
import { el } from '../shared/dom';
import type { ContextoRuta } from '../shared/router';
import {
  type Campo,
  crearAvisos,
  crearBoton,
  crearCampo,
  limpiarErroresCampos,
  mostrarErroresCampos,
  mostrarErrorServidor,
} from '../admin/configuracion/formulario';
import { NOMBRE_MAX } from '../admin/pacientes.logica';
import { listarPrestacionesPublicas, obtenerDisponibilidad, reservarTurno } from './reservar.api';
import './reservar.css';
import {
  type EntradaReserva,
  accionDeError,
  armarReserva,
  formatearFechaTurno,
  formatearHora,
  textoEstado,
  validarFecha,
  validarReserva,
  ventanaFechas,
} from './reservar.logica';

export interface OpcionesReserva {
  /** Reloj de la pantalla (para fijar la fecha en los tests). */
  readonly ahora?: () => Date;
}

const FINALIDAD =
  'Acepto que el consultorio use mis datos (nombre, apellido, DNI, email y teléfono) solo para gestionar este turno y enviarme avisos por email, según la Ley 25.326.';
const MENSAJE_SIN_PRESTACIONES = 'Por ahora no hay prestaciones disponibles para reservar.';
const MENSAJE_SIN_HORARIOS = 'No hay horarios disponibles ese día. Probá con otra fecha.';
const EMAIL_MAX = 150;

export function render(contenedor: HTMLElement, _ctx: ContextoRuta): void {
  void iniciar(contenedor);
}

/** Monta la pantalla y resuelve cuando termina la carga de prestaciones (usado por los tests). */
export function iniciar(contenedor: HTMLElement, opciones: OpcionesReserva = {}): Promise<void> {
  const ahora = opciones.ahora ?? (() => new Date());
  const ventana = ventanaFechas(ahora());

  let prestaciones: readonly PrestacionPublica[] = [];
  let prestacion: PrestacionPublica | null = null;
  let horario: string | null = null;
  let pedidoDisponibilidad = 0;
  let enviando = false;

  const avisos = crearAvisos();

  // --- 1. Prestacion
  const grupoPrestaciones = el('fieldset', { id: 'res-prestaciones', class: 'reserva-grupo' }, el('legend', {}, 'Prestación'));
  const estadoPrestaciones = el('p', { class: 'reserva-nota' });
  const seccionPrestacion = el('section', { class: 'reserva-seccion', 'aria-labelledby': 'res-titulo-prestacion' },
    el('h2', { id: 'res-titulo-prestacion' }, 'Prestación'),
    grupoPrestaciones,
    estadoPrestaciones,
  );

  // --- 2. Fecha y horario
  const fecha = crearCampo('res', 'fecha', 'Fecha', el('input', { type: 'date', min: ventana.min, max: ventana.max, required: '' }));
  const verHorarios = crearBoton('Ver horarios');
  const estadoHorarios = el('p', { id: 'res-horarios-estado', role: 'status', class: 'reserva-nota', tabindex: '-1' });
  const grupoHorarios = el('fieldset', { id: 'res-horarios', class: 'reserva-grupo reserva-horarios', tabindex: '-1' },
    el('legend', {}, 'Horarios disponibles'),
  );
  grupoHorarios.hidden = true;
  const seccionFecha = el('section', { class: 'reserva-seccion', 'aria-labelledby': 'res-titulo-fecha' },
    el('h2', { id: 'res-titulo-fecha' }, 'Fecha y horario'),
    el('div', { class: 'reserva-fecha' }, fecha.raiz, verHorarios),
    estadoHorarios,
    grupoHorarios,
  );
  seccionFecha.hidden = true;

  // --- 3. Datos
  const nombre = crearCampo('res', 'nombre', 'Nombre', el('input', {
    type: 'text', autocomplete: 'given-name', maxlength: String(NOMBRE_MAX), required: '',
  }));
  const apellido = crearCampo('res', 'apellido', 'Apellido', el('input', {
    type: 'text', autocomplete: 'family-name', maxlength: String(NOMBRE_MAX), required: '',
  }));
  const dni = crearCampo('res', 'dni', 'DNI', el('input', { type: 'text', inputmode: 'numeric', autocomplete: 'off', required: '' }));
  const email = crearCampo('res', 'email', 'Email', el('input', {
    type: 'email', autocomplete: 'email', maxlength: String(EMAIL_MAX), required: '',
  }));
  const telefono = crearCampo('res', 'telefono', 'Teléfono', el('input', { type: 'tel', autocomplete: 'tel', required: '' }));
  const consentimiento = crearCasilla();
  const campos: Record<string, Campo> = { nombre, apellido, dni, email, telefono, consentimiento };
  const enviar = crearBoton('Reservar', { tipo: 'submit' });
  const formulario = el('form', { novalidate: '', class: 'cfg-form reserva-form' },
    el('div', { class: 'reserva-datos' }, nombre.raiz, apellido.raiz, dni.raiz, email.raiz, telefono.raiz),
    consentimiento.raiz,
    el('div', { class: 'cfg-acciones' }, enviar),
  );
  const seccionDatos = el('section', { class: 'reserva-seccion', 'aria-labelledby': 'res-titulo-datos' },
    el('h2', { id: 'res-titulo-datos' }, 'Tus datos'),
    formulario,
  );
  seccionDatos.hidden = true;

  const pasos = el('div', { class: 'reserva-pasos' }, seccionPrestacion, seccionFecha, seccionDatos);
  contenedor.replaceChildren(el('div', { class: 'reserva' }, el('h1', {}, 'Reservar turno'), avisos.raiz, pasos));

  // --- Prestaciones

  async function cargarPrestaciones(): Promise<boolean> {
    avisos.informar('Cargando prestaciones…');
    estadoPrestaciones.textContent = '';
    try {
      prestaciones = await listarPrestacionesPublicas();
    } catch (error) {
      const reintentar = crearBoton('Reintentar');
      reintentar.addEventListener('click', () => void cargarPrestaciones());
      avisos.fallar(accionDeError(error).mensaje, reintentar);
      return false;
    }
    avisos.limpiar();
    pintarPrestaciones();
    return true;
  }

  function pintarPrestaciones(): void {
    grupoPrestaciones.replaceChildren(el('legend', {}, 'Prestación'));
    if (prestaciones.length === 0) {
      grupoPrestaciones.hidden = true;
      estadoPrestaciones.textContent = MENSAJE_SIN_PRESTACIONES;
      return;
    }
    grupoPrestaciones.hidden = false;
    for (const p of prestaciones) {
      const radio = el('input', { type: 'radio', name: 'prestacion', value: String(p.id) });
      radio.addEventListener('change', () => elegirPrestacion(p));
      grupoPrestaciones.append(el('label', { class: 'reserva-opcion' }, radio, el('span', {}, `${p.nombre} (${p.duracionMinutos} min)`)));
    }
  }

  function elegirPrestacion(elegida: PrestacionPublica): void {
    prestacion = elegida;
    fecha.control.value = '';
    limpiarErroresCampos({ fecha });
    descartarHorarios();
    seccionFecha.hidden = false;
  }

  /** Vuelve al paso 1 conservando lo escrito en el formulario de datos. */
  function reiniciarPasos(): void {
    prestacion = null;
    prestaciones = [];
    fecha.control.value = '';
    limpiarErroresCampos({ fecha });
    descartarHorarios();
    seccionFecha.hidden = true;
    grupoPrestaciones.replaceChildren(el('legend', {}, 'Prestación'));
  }

  // --- Horarios

  function descartarHorarios(): void {
    pedidoDisponibilidad += 1;
    horario = null;
    estadoHorarios.textContent = '';
    grupoHorarios.replaceChildren(el('legend', {}, 'Horarios disponibles'));
    grupoHorarios.hidden = true;
    seccionDatos.hidden = true;
  }

  async function buscarHorarios(enfocar = false): Promise<void> {
    if (prestacion === null) {
      return;
    }
    const valor = fecha.control.value;
    const error = validarFecha(valor, ventanaFechas(ahora()));
    descartarHorarios();
    if (error !== undefined) {
      mostrarErroresCampos({ fecha }, { fecha: error });
      return;
    }
    limpiarErroresCampos({ fecha });
    const pedido = pedidoDisponibilidad;
    estadoHorarios.textContent = 'Buscando horarios…';
    try {
      const disponibilidad = await obtenerDisponibilidad(prestacion.id, valor);
      if (pedido !== pedidoDisponibilidad) {
        return;
      }
      const dia = disponibilidad.dias.find((d) => d.fecha === valor);
      pintarHorarios(dia?.horarios ?? [], enfocar);
    } catch (e) {
      if (pedido !== pedidoDisponibilidad) {
        return;
      }
      estadoHorarios.textContent = '';
      await aplicarEfecto(e);
    }
  }

  function pintarHorarios(horarios: readonly string[], enfocar: boolean): void {
    if (horarios.length === 0) {
      estadoHorarios.textContent = MENSAJE_SIN_HORARIOS;
      if (enfocar) {
        estadoHorarios.focus();
      }
      return;
    }
    estadoHorarios.textContent = '';
    for (const instante of horarios) {
      const radio = el('input', { type: 'radio', name: 'horario', value: instante });
      radio.addEventListener('change', () => {
        horario = instante;
        seccionDatos.hidden = false;
      });
      grupoHorarios.append(el('label', { class: 'reserva-opcion reserva-hora' }, radio, el('span', {}, formatearHora(instante))));
    }
    grupoHorarios.hidden = false;
    if (enfocar) {
      grupoHorarios.focus();
    }
  }

  // --- Errores

  /** Aplica el efecto de un error de la API sobre la pantalla (y muestra su mensaje fijo). */
  async function aplicarEfecto(error: unknown): Promise<void> {
    const { mensaje, efecto } = accionDeError(error);
    switch (efecto) {
      case 'campos':
        mostrarErrorServidor(campos, avisos, error);
        return;
      case 'consentimiento':
        mostrarErroresCampos(campos, { consentimiento: mensaje });
        return;
      case 'refrescarHorarios':
        avisos.fallar(mensaje);
        await buscarHorarios(true);
        return;
      case 'recargarPrestaciones':
        reiniciarPasos();
        if (await cargarPrestaciones()) {
          avisos.fallar(mensaje);
        }
        return;
      default:
        avisos.fallar(mensaje);
    }
  }

  // --- Envio

  function leerEntrada(): EntradaReserva {
    return {
      nombre: nombre.control.value,
      apellido: apellido.control.value,
      dni: dni.control.value,
      email: email.control.value,
      telefono: telefono.control.value,
      consentimiento: consentimiento.control instanceof HTMLInputElement && consentimiento.control.checked,
    };
  }

  async function enviarReserva(): Promise<void> {
    if (enviando || prestacion === null || horario === null) {
      return;
    }
    const entrada = leerEntrada();
    const errores = validarReserva(entrada);
    if (Object.keys(errores).length > 0) {
      avisos.limpiar();
      mostrarErroresCampos(campos, errores);
      return;
    }
    limpiarErroresCampos(campos);
    avisos.limpiar();
    enviando = true;
    enviar.disabled = true;
    enviar.textContent = 'Reservando…';
    enviar.setAttribute('aria-busy', 'true');
    try {
      const resumen = await reservarTurno(armarReserva(entrada, prestacion.id, horario));
      pedidoDisponibilidad += 1;
      mostrarResumen(resumen, entrada.email.trim());
    } catch (error) {
      await aplicarEfecto(error);
    } finally {
      enviando = false;
      enviar.disabled = false;
      enviar.textContent = 'Reservar';
      enviar.removeAttribute('aria-busy');
    }
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void enviarReserva();
  });
  fecha.control.addEventListener('change', () => void buscarHorarios());
  verHorarios.addEventListener('click', () => void buscarHorarios());

  // --- Resumen

  function mostrarResumen(resumen: TurnoResumenPublico, emailIngresado: string): void {
    avisos.limpiar();
    const titulo = el('h2', { tabindex: '-1' }, `¡Listo, ${resumen.nombrePaciente}! Tu turno quedó reservado`);
    const otro = crearBoton('Reservar otro turno');
    otro.addEventListener('click', () => void iniciar(contenedor, opciones));
    const fila = (termino: string, valor: string): Node[] => [el('dt', {}, termino), el('dd', {}, valor)];
    const detalle = el('dl', { class: 'reserva-detalle' },
      ...fila('Prestación', `${resumen.prestacion.nombre} (${resumen.prestacion.duracionMinutos} min)`),
      ...fila('Fecha', formatearFechaTurno(resumen.inicio)),
      ...fila('Hora', formatearHora(resumen.inicio)),
      ...fila('Estado', textoEstado(resumen.estado)),
      ...fila('Contacto del consultorio', resumen.contactoConsultorio),
    );
    const notas: Node[] = [
      el('p', {}, `Revisá tu email (${emailIngresado}): te enviamos los datos del turno y los links para gestionarlo.`),
    ];
    if (resumen.estado === 'CONFIRMADO') {
      notas.push(el('p', {}, 'Si necesitás cambiarlo, comunicate con el consultorio.'));
    }
    pasos.replaceChildren(el('section', { class: 'reserva-seccion reserva-resumen' }, titulo, detalle, ...notas, otro));
    titulo.focus();
  }

  return cargarPrestaciones().then(() => undefined);
}

/** Casilla de consentimiento con su texto de finalidad y su mensaje de error (misma forma que un `Campo`). */
function crearCasilla(): Campo {
  const control = el('input', { type: 'checkbox', id: 'res-consentimiento', name: 'consentimiento', required: '' });
  const error = el('p', { id: 'res-consentimiento-error', class: 'cfg-error' });
  control.setAttribute('aria-describedby', error.id);
  const raiz = el('div', { class: 'cfg-campo reserva-consentimiento' },
    el('div', { class: 'reserva-casilla' }, control, el('label', { for: control.id }, FINALIDAD)),
    error,
  );
  return { raiz, control, error };
}
