import './agenda.css';
import { ErrorApi } from '../../api/cliente';
import type { HorarioAtencion } from '../../api/types';
import { el, montar } from '../../shared/dom';
import type { ContextoRuta } from '../../shared/router';
import { hoyBA, sumarDias } from '../configuracion/fechas';
import { mensajeDeError } from '../configuracion/errores';
import { cargarDatosAgenda, type DatosAgenda } from './agenda.api';
import {
  agruparTurnos,
  cantidadUrgentes,
  claseEstado,
  esUrgente,
  etiquetaDia,
  etiquetaEstado,
  formatearFechaLarga,
  formatearHora,
  franjasDelDia,
  inicioSemana,
  minutosDeInstante,
  rangoDiario,
  rangosAtencion,
  rangoSemanal,
  turnosVisibles,
} from './agenda.logica';

type Vista = 'dia' | 'semana';

interface Estado {
  vista: Vista;
  fecha: string;
  mostrarCancelados: boolean;
  cargando: boolean;
  datos: DatosAgenda | null;
  error: unknown | null;
}

interface Entrada {
  readonly minuto: number;
  readonly orden: number;
  readonly nodo: HTMLElement;
}

function rangoActual(estado: Estado): { readonly desde: string; readonly hasta: string } {
  return estado.vista === 'dia' ? rangoDiario(estado.fecha) : rangoSemanal(estado.fecha);
}

function crearTarjeta(turno: import('../../api/types').Turno, compacta: boolean): HTMLElement {
  const urgente = esUrgente(turno);
  const cancelado = turno.estado === 'CANCELADO';
  const clases = ['agenda-turno'];
  if (compacta) {
    clases.push('agenda-turno--compacta');
  }
  if (urgente) {
    clases.push('agenda-turno--urgente');
  }
  if (cancelado) {
    clases.push('agenda-turno--cancelado');
  }

  const articulo = el('article', {
    class: clases.join(' '),
    'data-turno-id': String(turno.id),
    'data-estado': turno.estado,
  });
  const hora = el('div', { class: 'agenda-turno__hora' }, `${formatearHora(turno.inicio)} – ${formatearHora(turno.fin)}`);
  const datos = el('div', { class: 'agenda-turno__datos' });
  const fila = el(
    'div',
    { class: 'agenda-fila' },
    el('span', { class: 'agenda-paciente' }, `${turno.paciente.nombre} ${turno.paciente.apellido}`),
    el('span', { class: claseEstado(turno) }, etiquetaEstado(turno)),
  );
  const detalle = el('p', { class: 'agenda-detalle' }, `${turno.prestacion.nombre} · teléfono ${turno.telefonoContacto}`);
  datos.append(fila, detalle);

  if (urgente) {
    const acciones = el('div', { class: 'agenda-acciones' });
    const confirmar = el('button', { type: 'button', 'data-enganche': 'confirmar', disabled: '' }, 'Confirmar (C-20)');
    const cancelar = el('button', { type: 'button', 'data-enganche': 'cancelar', disabled: '' }, 'Cancelar (C-20)');
    acciones.append(confirmar, cancelar);
    datos.append(acciones);
  }

  articulo.append(hora, datos);
  return articulo;
}

function pintarDia(
  fecha: string,
  turnosDelDia: readonly import('../../api/types').Turno[],
  horarios: readonly HorarioAtencion[],
  bloqueos: readonly import('../../api/types').Bloqueo[],
  compacta: boolean,
): HTMLElement {
  const id = `agenda-dia-${fecha}`;
  const seccion = el('section', { class: 'agenda-dia', 'aria-labelledby': id });
  seccion.append(
    el('h3', { id, class: 'agenda-dia__titulo' }, compacta ? etiquetaDia(fecha) : formatearFechaLarga(fecha)),
  );

  if (rangosAtencion(fecha, horarios).length === 0) {
    seccion.append(el('p', { class: 'agenda-sin-atencion' }, 'Sin atención configurada'));
  }

  const entradas: Entrada[] = [];
  for (const franja of franjasDelDia(fecha, horarios, bloqueos)) {
    const motivo = franja.tipo === 'bloqueo' ? (franja.motivo === null ? '' : `: ${franja.motivo}`) : '';
    const texto =
      franja.tipo === 'bloqueo'
        ? `${franja.inicio} – ${franja.fin} · Bloqueo${motivo}`
        : `${franja.inicio} – ${franja.fin} · Fuera del horario de atención`;
    entradas.push({
      minuto: franja.inicioMinutos,
      orden: 0,
      nodo: el('div', { class: `agenda-franja agenda-franja--${franja.tipo}` }, texto),
    });
  }
  for (const turno of turnosDelDia) {
    entradas.push({
      minuto: minutosDeInstante(turno.inicio) ?? 0,
      orden: 1,
      nodo: crearTarjeta(turno, compacta),
    });
  }
  entradas.sort((a, b) => a.minuto - b.minuto || a.orden - b.orden);
  for (const entrada of entradas) {
    seccion.append(entrada.nodo);
  }
  if (entradas.length === 0) {
    seccion.append(el('p', { class: 'agenda-sin-turnos' }, 'Sin turnos'));
  }
  return seccion;
}

export function render(contenedor: HTMLElement, ctx: ContextoRuta): void {
  const vistaInicial: Vista = ctx.query.get('ver') === 'semana' ? 'semana' : 'dia';
  const hoy = hoyBA();
  const estado: Estado = {
    vista: vistaInicial,
    fecha: vistaInicial === 'dia' ? hoy : inicioSemana(hoy),
    mostrarCancelados: false,
    cargando: true,
    datos: null,
    error: null,
  };

  const titulo = el('h1', { id: 'agenda-titulo', class: 'agenda-titulo' }, 'Agenda');
  const anterior = el('button', { type: 'button', class: 'agenda-control' }, '‹');
  const etiqueta = el('time', { class: 'agenda-periodo__etiqueta' });
  const siguiente = el('button', { type: 'button', class: 'agenda-control' }, '›');
  const actual = el('button', { type: 'button', class: 'agenda-control' }, 'Hoy');
  const periodo = el('div', { class: 'agenda-periodo' }, anterior, etiqueta, siguiente, actual);

  const botonDia = el('button', { type: 'button', class: 'agenda-control' }, 'Día');
  const botonSemana = el('button', { type: 'button', class: 'agenda-control' }, 'Semana');
  const vista = el('div', { class: 'agenda-vista', role: 'group', 'aria-label': 'Tipo de vista' }, botonDia, botonSemana);
  const cancelados = el('button', { type: 'button', class: 'agenda-control' }, 'Mostrar cancelados');
  const barra = el('div', { class: 'agenda-barra' }, periodo, vista, cancelados);

  const aviso = el('p', { class: 'agenda-aviso', role: 'status' });
  const contenido = el('div', { class: 'agenda-contenido' });
  const raiz = el('section', { class: 'agenda', 'aria-labelledby': 'agenda-titulo' }, titulo, barra, aviso, contenido);
  montar(contenedor, raiz);

  function botonReintentar(): HTMLElement {
    const reintentar = el('button', { type: 'button', class: 'agenda-control' }, 'Reintentar');
    reintentar.addEventListener('click', () => void cargar());
    return reintentar;
  }

  function pintarError(mensaje: string): void {
    montar(contenido, el('div', { class: 'agenda-error', role: 'alert' }, mensaje), botonReintentar());
  }

  function pintarDatos(datos: DatosAgenda): void {
    const piezas: HTMLElement[] = [];
    if (datos.errores.length > 0) {
      piezas.push(
        el(
          'div',
          { class: 'agenda-error', role: 'alert' },
          'No se pudieron cargar algunos datos de la agenda. Reintentá para completar la vista.',
        ),
        botonReintentar(),
      );
    }
    if (datos.turnos === null) {
      piezas.push(el('p', { class: 'agenda-vacio', role: 'status' }, 'No se pudo cargar la lista de turnos.'));
      montar(contenido, ...piezas);
      return;
    }

    const visibles = turnosVisibles(datos.turnos, estado.mostrarCancelados);
    const horarios = datos.horarios ?? [];
    const bloqueos = datos.bloqueos ?? [];

    if (visibles.length === 0) {
      piezas.push(
        el(
          'p',
          { class: 'agenda-vacio', role: 'status' },
          estado.vista === 'dia' ? 'No hay turnos para este día.' : 'No hay turnos para esta semana.',
        ),
      );
    }

    if (estado.vista === 'dia') {
      const porDia = agruparTurnos(visibles, [estado.fecha]);
      piezas.push(pintarDia(estado.fecha, porDia.get(estado.fecha) ?? [], horarios, bloqueos, false));
    } else {
      const rango = rangoSemanal(estado.fecha);
      const fechas: string[] = [];
      for (let fecha = rango.desde; fecha <= rango.hasta; fecha = sumarDias(fecha, 1)) {
        fechas.push(fecha);
      }
      const porDia = agruparTurnos(visibles, fechas);
      const grilla = el('div', { class: 'agenda-semana' });
      for (const fecha of fechas) {
        grilla.append(pintarDia(fecha, porDia.get(fecha) ?? [], horarios, bloqueos, true));
      }
      piezas.push(grilla);
    }
    montar(contenido, ...piezas);
  }

  function pintar(): void {
    const rango = rangoActual(estado);
    anterior.setAttribute('aria-label', estado.vista === 'dia' ? 'Día anterior' : 'Semana anterior');
    siguiente.setAttribute('aria-label', estado.vista === 'dia' ? 'Día siguiente' : 'Semana siguiente');
    actual.textContent = estado.vista === 'dia' ? 'Hoy' : 'Esta semana';
    etiqueta.setAttribute('datetime', estado.vista === 'dia' ? estado.fecha : rango.desde);
    etiqueta.textContent =
      estado.vista === 'dia'
        ? formatearFechaLarga(estado.fecha)
        : `Semana del ${formatearFechaLarga(rango.desde)} al ${formatearFechaLarga(rango.hasta)}`;
    botonDia.setAttribute('aria-pressed', String(estado.vista === 'dia'));
    botonSemana.setAttribute('aria-pressed', String(estado.vista === 'semana'));
    cancelados.setAttribute('aria-pressed', String(estado.mostrarCancelados));

    if (estado.cargando) {
      aviso.textContent = 'Cargando agenda…';
    } else {
      const urgentes = estado.datos?.turnos === null || estado.datos === null ? 0 : cantidadUrgentes(estado.datos.turnos);
      aviso.textContent =
        urgentes === 0
          ? ''
          : `Hay ${urgentes} ${urgentes === 1 ? 'turno' : 'turnos'} sin confirmar que empieza${
              urgentes === 1 ? '' : 'n'
            } en menos de 24 h. Verificá el teléfono de contacto.`;
    }

    contenido.replaceChildren();
    if (estado.cargando) {
      return;
    }
    if (estado.error !== null) {
      pintarError(mensajeDeError(estado.error));
      return;
    }
    if (estado.datos !== null) {
      pintarDatos(estado.datos);
    }
  }

  async function cargar(): Promise<void> {
    estado.cargando = true;
    estado.datos = null;
    estado.error = null;
    pintar();
    let expirado = false;
    try {
      estado.datos = await cargarDatosAgenda(rangoActual(estado));
    } catch (error) {
      if (error instanceof ErrorApi && error.status === 401 && error.codigo === 'NO_AUTENTICADO') {
        expirado = true;
        return;
      }
      estado.error = error;
    } finally {
      estado.cargando = false;
      if (!expirado) {
        pintar();
      }
    }
  }

  function mover(direccion: number): void {
    const pasos = estado.vista === 'dia' ? direccion : direccion * 7;
    estado.fecha = sumarDias(estado.fecha, pasos);
    void cargar();
  }

  function cambiarVista(vista: Vista): void {
    if (estado.vista === vista) {
      return;
    }
    estado.vista = vista;
    const hoyActual = hoyBA();
    estado.fecha = vista === 'dia' ? hoyActual : inicioSemana(hoyActual);
    void cargar();
  }

  anterior.addEventListener('click', () => mover(-1));
  siguiente.addEventListener('click', () => mover(1));
  actual.addEventListener('click', () => {
    const hoyActual = hoyBA();
    estado.fecha = estado.vista === 'dia' ? hoyActual : inicioSemana(hoyActual);
    void cargar();
  });
  botonDia.addEventListener('click', () => cambiarVista('dia'));
  botonSemana.addEventListener('click', () => cambiarVista('semana'));
  cancelados.addEventListener('click', () => {
    estado.mostrarCancelados = !estado.mostrarCancelados;
    pintar();
  });

  void cargar();
}
