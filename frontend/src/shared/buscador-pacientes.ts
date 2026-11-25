import { ErrorConexion, MENSAJE_GENERICO } from '../api/cliente';
import type { Paciente } from '../api/types';
import './buscador-pacientes.css';
import { el } from './dom';

/**
 * Buscador reutilizable de pacientes (pantalla Pacientes y carga manual de turnos).
 * No conoce la API: la busqueda se inyecta por parametro, asi este modulo no depende de las pantallas del area privada.
 */
export interface OpcionesBuscadorPacientes {
  /** Prefijo de los ids del DOM: debe ser distinto en cada instancia de la misma pagina. */
  readonly prefijo: string;
  readonly buscar: (q: string) => Promise<readonly Paciente[]>;
  readonly alSeleccionar: (paciente: Paciente) => void;
  /** Texto del label del campo. Por defecto "Buscar por DNI o apellido". */
  readonly etiqueta?: string;
  /** Texto del boton de cada resultado. Por defecto "Ver". */
  readonly textoSeleccionar?: string;
}

export interface BuscadorPacientes {
  readonly raiz: HTMLElement;
  /** Busca `q`; sin argumento repite la ultima busqueda. Resuelve cuando termina (o se descarta). */
  buscar(q?: string): Promise<void>;
  enfocar(): void;
}

/** 99999997 -> 99.999.997 (el valor guardado son solo digitos; los puntos son solo para leer mejor). */
function dniConPuntos(dni: string): string {
  return /^\d{7,8}$/.test(dni) ? dni.replace(/\B(?=(\d{3})+$)/g, '.') : dni;
}

function mensajeDeFallo(error: unknown): string {
  return error instanceof ErrorConexion ? error.message : MENSAJE_GENERICO;
}

export function crearBuscadorPacientes(opciones: OpcionesBuscadorPacientes): BuscadorPacientes {
  const idCampo = opciones.prefijo + '-q';
  const textoSeleccionar = opciones.textoSeleccionar ?? 'Ver';
  let ultimaBusqueda = '';
  let numeroBusqueda = 0;

  const campo = el('input', { id: idCampo, name: 'q', type: 'search', autocomplete: 'off', class: 'bp-campo' });
  const enviar = el('button', { type: 'submit' }, 'Buscar');
  const formulario = el(
    'form',
    { role: 'search', class: 'bp-form' },
    el('label', { for: idCampo }, opciones.etiqueta ?? 'Buscar por DNI o apellido'),
    el('div', { class: 'bp-fila' }, campo, enviar),
  );
  const estado = el('p', { role: 'status', class: 'bp-estado' });
  const alerta = el('p', { role: 'alert', class: 'bp-alerta' });
  const accion = el('div', { class: 'bp-accion' });
  const lista = el('ul', { class: 'bp-lista' });
  const raiz = el('div', { class: 'bp' }, formulario, estado, alerta, accion, lista);

  function pintarResultados(pacientes: readonly Paciente[]): void {
    const items = pacientes.map((p) => {
      const nombre = p.apellido + ', ' + p.nombre;
      const seleccionar = el('button', { type: 'button', 'aria-label': textoSeleccionar + ' ' + nombre }, textoSeleccionar);
      seleccionar.addEventListener('click', () => opciones.alSeleccionar(p));
      return el(
        'li',
        { class: 'bp-item' },
        el('div', { class: 'bp-datos' }, el('strong', {}, nombre), el('span', {}, 'DNI ' + dniConPuntos(p.dni))),
        seleccionar,
      );
    });
    lista.replaceChildren(...items);
  }

  async function buscar(q?: string): Promise<void> {
    const texto = (q ?? ultimaBusqueda).trim();
    ultimaBusqueda = texto;
    const actual = ++numeroBusqueda;
    alerta.textContent = '';
    accion.replaceChildren();
    estado.textContent = 'Buscando…';
    try {
      const pacientes = await opciones.buscar(texto);
      if (actual !== numeroBusqueda) {
        return;
      }
      pintarResultados(pacientes);
      estado.textContent =
        pacientes.length === 0
          ? 'No hay pacientes que coincidan con la búsqueda.'
          : pacientes.length === 1
            ? '1 paciente encontrado'
            : pacientes.length + ' pacientes encontrados';
    } catch (error) {
      if (actual !== numeroBusqueda) {
        return;
      }
      lista.replaceChildren();
      estado.textContent = '';
      alerta.textContent = mensajeDeFallo(error);
      const reintentar = el('button', { type: 'button' }, 'Reintentar');
      reintentar.addEventListener('click', () => void buscar());
      accion.replaceChildren(reintentar);
    }
  }

  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    void buscar(campo.value);
  });

  return {
    raiz,
    buscar,
    enfocar: () => campo.focus(),
  };
}
