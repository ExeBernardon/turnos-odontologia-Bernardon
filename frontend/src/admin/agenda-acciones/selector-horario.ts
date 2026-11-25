import { el } from '../../shared/dom';
import { formatearHora } from '../agenda/agenda.logica';
import { hoyBA } from '../configuracion/fechas';
import { obtenerDisponibilidadAdmin } from './acciones.api';
import { accionDeErrorTurno } from './acciones.logica';

export interface OpcionesSelectorHorario {
  /** Prefijo de los ids del DOM: debe ser distinto en cada instancia de la misma pagina. */
  readonly prefijo: string;
  readonly prestacionId: number | null;
  readonly ahora: () => Date;
  /** Recibe el instante exacto que devolvio la API, o `null` cuando se descarta la eleccion. */
  readonly alElegir: (instante: string | null) => void;
}

export interface SelectorHorario {
  readonly raiz: HTMLElement;
  /** Vuelve a pedir el mismo dia (tras un 409 o 422 de horario) y limpia la eleccion. */
  refrescar(): Promise<void>;
  /** Descarta la eleccion; si ya se habian pedido horarios, los vuelve a pedir para la nueva prestacion. */
  cambiarPrestacion(prestacionId: number | null): Promise<void>;
  elegido(): string | null;
}

/** Dia + horarios libres del odontologo (endpoint privado, sin la ventana del paciente). Lo usan el alta y la reprogramacion. */
export function crearSelectorHorario(opciones: OpcionesSelectorHorario): SelectorHorario {
  const idDia = opciones.prefijo + '-dia';
  let prestacionId = opciones.prestacionId;
  let consultado = false;
  let elegido: string | null = null;
  let numeroConsulta = 0;

  const dia = el('input', { id: idDia, name: 'dia', type: 'date', min: hoyBA(opciones.ahora()) });
  dia.value = hoyBA(opciones.ahora());
  const ver = el('button', { type: 'button', class: 'aa-boton' }, 'Ver horarios');
  const estado = el('p', { role: 'status', class: 'aa-estado' });
  const alerta = el('p', { role: 'alert', class: 'aa-alerta' });
  const accion = el('div', { class: 'aa-alerta-accion' });
  const lista = el('div', { class: 'aa-horarios' });
  const raiz = el(
    'div',
    { class: 'aa-selector' },
    el('div', { class: 'aa-fila' }, el('label', { for: idDia }, 'Día'), dia, ver),
    estado,
    alerta,
    accion,
    lista,
  );

  function elegir(instante: string | null): void {
    elegido = instante;
    opciones.alElegir(instante);
  }

  function pintarHorarios(horarios: readonly string[]): void {
    const grupo = el('fieldset', { class: 'aa-horarios__grupo' }, el('legend', {}, 'Horarios libres'));
    horarios.forEach((instante, i) => {
      const id = `${opciones.prefijo}-horario-${i}`;
      const radio = el('input', { type: 'radio', id, name: opciones.prefijo + '-horario', value: instante });
      radio.addEventListener('change', () => elegir(instante));
      grupo.append(el('div', { class: 'aa-horario' }, radio, el('label', { for: id }, formatearHora(instante))));
    });
    lista.replaceChildren(grupo);
  }

  async function consultar(): Promise<void> {
    const actual = ++numeroConsulta;
    alerta.textContent = '';
    accion.replaceChildren();
    lista.replaceChildren();
    if (elegido !== null) {
      elegir(null);
    }
    if (prestacionId === null) {
      estado.textContent = 'Elegí una prestación para ver los horarios.';
      return;
    }
    if (dia.value === '') {
      estado.textContent = 'Elegí un día para ver los horarios.';
      return;
    }
    consultado = true;
    estado.textContent = 'Buscando horarios…';
    try {
      const respuesta = await obtenerDisponibilidadAdmin(prestacionId, dia.value);
      if (actual !== numeroConsulta) {
        return;
      }
      const horarios = respuesta.dias.find((d) => d.fecha === dia.value)?.horarios ?? [];
      if (horarios.length === 0) {
        estado.textContent = 'No hay horarios libres ese día';
        return;
      }
      estado.textContent = horarios.length === 1 ? '1 horario libre' : `${horarios.length} horarios libres`;
      pintarHorarios(horarios);
    } catch (error) {
      if (actual !== numeroConsulta) {
        return;
      }
      estado.textContent = '';
      alerta.textContent = accionDeErrorTurno(error, 'alta').mensaje;
      const reintentar = el('button', { type: 'button', class: 'aa-boton' }, 'Reintentar');
      reintentar.addEventListener('click', () => void consultar());
      accion.replaceChildren(reintentar);
    }
  }

  ver.addEventListener('click', () => void consultar());

  return {
    raiz,
    refrescar: consultar,
    async cambiarPrestacion(nuevaId) {
      prestacionId = nuevaId;
      if (consultado || elegido !== null) {
        await consultar();
      }
    },
    elegido: () => elegido,
  };
}
