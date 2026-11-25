import './acciones.css';
import type { Turno } from '../../api/types';
import { el } from '../../shared/dom';
import { formatearHora } from '../agenda/agenda.logica';
import { cancelarTurno, confirmarTurno, marcarAsistencia } from './acciones.api';
import {
  type AccionTurno,
  accionDeErrorTurno,
  accionesPermitidas,
  type ClaveError,
  describirHorario,
  textoResultado,
} from './acciones.logica';
import { montarAltaManual } from './alta-manual';
import { confirmarAccion } from './dialogo';
import { montarReprogramar } from './reprogramar';

export interface OpcionesAccionesAgenda {
  /** Vuelve a pedir y pintar la agenda; resuelve cuando termino. */
  readonly recargar: () => Promise<void>;
  /** Reloj inyectable para los tests. */
  readonly ahora?: () => Date;
}

/** Piezas que la agenda monta y el bloque de acciones de cada tarjeta. */
export interface AccionesAgenda {
  /** Va dentro de la barra de la agenda: boton "Nuevo turno". */
  readonly barra: HTMLElement;
  /** Region `role="status"` con el resultado de las acciones; recibe el foco si la tarjeta ya no esta. */
  readonly aviso: HTMLElement;
  /** Region `role="alert"` con los errores de las acciones. */
  readonly alerta: HTMLElement;
  /** Contenedor de los formularios (alta manual y reprogramacion). */
  readonly panel: HTMLElement;
  /** Botones de una tarjeta, o `null` si el turno no admite acciones. */
  deTurno(turno: Turno, compacta: boolean): HTMLElement | null;
}

export function crearAccionesAgenda(opciones: OpcionesAccionesAgenda): AccionesAgenda {
  const ahora = opciones.ahora ?? ((): Date => new Date());

  const nuevo = el('button', { type: 'button', class: 'aa-boton aa-boton--primario' }, 'Nuevo turno');
  const barra = el('div', { class: 'aa-barra' }, nuevo);
  const aviso = el('p', { class: 'aa-aviso', role: 'status', tabindex: '-1' });
  const alerta = el('p', { class: 'aa-alerta', role: 'alert' });
  const panel = el('div', { class: 'aa-panel-contenedor' });

  function anunciar(texto: string): void {
    alerta.textContent = '';
    aviso.textContent = '';
    aviso.textContent = texto;
  }

  function alertar(texto: string): void {
    aviso.textContent = '';
    alerta.textContent = texto;
  }

  function enfocarTurnoOAviso(id: number): void {
    const tarjeta = document.querySelector<HTMLElement>(`[data-turno-id="${id}"]`);
    if (tarjeta === null) {
      aviso.focus();
      return;
    }
    tarjeta.setAttribute('tabindex', '-1');
    tarjeta.focus();
  }

  async function recargarYEnfocar(id: number): Promise<void> {
    await opciones.recargar();
    enfocarTurnoOAviso(id);
  }

  function devolverFoco(disparador: HTMLElement): () => void {
    return () => {
      if (disparador.isConnected) {
        disparador.focus();
      }
    };
  }

  nuevo.addEventListener('click', () => {
    montarAltaManual(panel, {
      ahora,
      anunciar,
      alCrear: (turno) => recargarYEnfocar(turno.id),
      alCerrar: devolverFoco(nuevo),
    });
  });

  /** Ejecuta una accion sobre un turno: bloquea los botones, anuncia el resultado y recarga la agenda. */
  async function ejecutar(
    bloque: HTMLElement,
    turno: Turno,
    clave: ClaveError,
    accion: AccionTurno,
    llamada: () => Promise<Turno>,
  ): Promise<void> {
    if (bloque.getAttribute('aria-busy') === 'true') {
      return;
    }
    const botones = Array.from(bloque.querySelectorAll('button')).filter((b) => !b.disabled);
    bloque.setAttribute('aria-busy', 'true');
    for (const b of botones) {
      b.disabled = true;
    }
    try {
      const resultado = await llamada();
      anunciar(textoResultado(resultado, accion));
      await recargarYEnfocar(turno.id);
    } catch (error) {
      const { mensaje, efecto } = accionDeErrorTurno(error, clave);
      if (mensaje !== '') {
        alertar(mensaje);
      }
      if (efecto === 'recargarAgenda') {
        await recargarYEnfocar(turno.id);
      }
    } finally {
      bloque.removeAttribute('aria-busy');
      for (const b of botones) {
        b.disabled = false;
      }
    }
  }

  function describir(turno: Turno): string {
    return `${turno.paciente.nombre} ${turno.paciente.apellido}`;
  }

  function deTurno(turno: Turno, compacta: boolean): HTMLElement | null {
    const permitidas = accionesPermitidas(turno, ahora());
    const hora = formatearHora(turno.inicio);
    const nombre = describir(turno);
    const bloque = el('div', { class: 'agenda-acciones aa-acciones' });

    function boton(enganche: string, texto: string, etiqueta: string, clase = ''): HTMLButtonElement {
      return el('button', {
        type: 'button',
        class: ('aa-boton ' + clase).trim(),
        'data-enganche': enganche,
        'aria-label': etiqueta,
      }, texto);
    }

    const principales: HTMLElement[] = [];
    const secundarias: HTMLElement[] = [];

    if (permitidas.confirmar) {
      const confirmar = boton('confirmar', 'Confirmar', `Confirmar turno de ${nombre}, ${hora}`, 'aa-boton--primario');
      confirmar.addEventListener('click', () => {
        void ejecutar(bloque, turno, 'confirmar', 'confirmar', () => confirmarTurno(turno.id));
      });
      principales.push(confirmar);
    }

    if (permitidas.cancelar) {
      const cancelar = boton('cancelar', 'Cancelar', `Cancelar turno de ${nombre}, ${hora}`, 'aa-boton--peligro');
      cancelar.addEventListener('click', () => {
        if (bloque.getAttribute('aria-busy') === 'true') {
          return;
        }
        void confirmarAccion({
          titulo: 'Cancelar turno',
          descripcion: `Vas a cancelar el turno de ${nombre} (${turno.prestacion.nombre}) del ${describirHorario(turno.inicio)}. Se enviará un aviso a ${turno.emailContacto}.`,
          aceptar: 'Cancelar turno',
          disparador: cancelar,
        }).then((acepto) => {
          if (acepto) {
            return ejecutar(bloque, turno, 'cancelar', 'cancelar', () => cancelarTurno(turno.id));
          }
          return undefined;
        });
      });
      principales.push(cancelar);
    }

    if (permitidas.reprogramar) {
      const reprogramar = boton('reprogramar', 'Reprogramar', `Reprogramar turno de ${nombre}, ${hora}`);
      reprogramar.addEventListener('click', () => {
        montarReprogramar(panel, {
          turno,
          ahora,
          anunciar,
          alertar,
          alCambiar: () => recargarYEnfocar(turno.id),
          alCerrar: devolverFoco(reprogramar),
        });
      });
      secundarias.push(reprogramar);
    }

    if (permitidas.asistencia.visible) {
      const idMotivo = `aa-desde-${turno.id}`;
      for (const [enganche, texto, estado, titulo, aceptar, accion] of [
        ['asistio', 'Asistió', 'ASISTIO', 'Marcar asistencia', 'Marcar asistencia', 'asistio'],
        ['ausente', 'Ausente', 'AUSENTE', 'Marcar ausencia', 'Marcar ausencia', 'ausente'],
      ] as const) {
        const marcar = boton(enganche, texto, `${texto}: ${nombre}, ${hora}`, enganche === 'ausente' ? 'aa-boton--peligro' : '');
        marcar.disabled = !permitidas.asistencia.habilitada;
        if (marcar.disabled) {
          marcar.setAttribute('aria-describedby', idMotivo);
        }
        marcar.addEventListener('click', () => {
          void confirmarAccion({
            titulo,
            descripcion: `Vas a registrar ${enganche === 'asistio' ? 'la asistencia' : 'la ausencia'} de ${nombre} al turno de ${turno.prestacion.nombre} del ${describirHorario(turno.inicio)}. No se puede deshacer.`,
            aceptar,
            disparador: marcar,
          }).then((acepto) => {
            if (acepto) {
              return ejecutar(bloque, turno, 'estado', accion, () => marcarAsistencia(turno.id, estado));
            }
            return undefined;
          });
        });
        secundarias.push(marcar);
      }
      if (!permitidas.asistencia.habilitada) {
        secundarias.push(
          el('p', { id: idMotivo, class: 'aa-ayuda' }, `Se puede marcar desde las ${permitidas.asistencia.desde}`),
        );
      }
    }

    if (principales.length === 0 && secundarias.length === 0) {
      return null;
    }
    bloque.append(...principales);
    if (compacta && secundarias.length > 0) {
      bloque.append(el('details', { class: 'aa-mas' }, el('summary', {}, 'Más acciones'), ...secundarias));
    } else {
      bloque.append(...secundarias);
    }
    return bloque;
  }

  return { barra, aviso, alerta, panel, deTurno };
}
