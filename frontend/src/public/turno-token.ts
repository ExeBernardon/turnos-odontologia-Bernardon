import type { TurnoResumenPublico } from '../api/types';
import { el } from '../shared/dom';
import type { ContextoRuta } from '../shared/router';
import { crearAvisos, crearBoton } from '../admin/configuracion/formulario';
import { cancelarTurnoPorToken, confirmarTurnoPorToken, obtenerTurnoPorToken } from './turno-token.api';
import { formatearFechaTurno, formatearHora, textoEstado } from './reservar.logica';
import './turno-token.css';
import { type AccionToken, accionDeErrorToken, leerToken, vistaDeTurno } from './turno-token.logica';

export interface OpcionesTurnoToken {
  /** Reloj de la pantalla (para fijar la fecha en los tests). */
  readonly ahora?: () => Date;
}

const TITULOS: Readonly<Record<AccionToken, string>> = { confirmar: 'Confirmar turno', cancelar: 'Cancelar turno' };
const TEXTO_BOTON: Readonly<Record<AccionToken, string>> = { confirmar: 'Confirmar asistencia', cancelar: 'Cancelar turno' };
const TEXTO_ENVIANDO: Readonly<Record<AccionToken, string>> = { confirmar: 'Confirmando…', cancelar: 'Cancelando…' };

/**
 * Monta la pagina de un link con token (`confirmar` o `cancelar`) y resuelve cuando termina la lectura inicial
 * (usado por los tests). Abrir el link solo hace `GET`: ninguna accion se ejecuta sin que el paciente la pida.
 * El token se usa unicamente para armar el pedido; nunca se escribe en el DOM.
 */
export function iniciarTurnoToken(
  contenedor: HTMLElement,
  accion: AccionToken,
  ctx: ContextoRuta,
  opciones: OpcionesTurnoToken = {},
): Promise<void> {
  const ahora = opciones.ahora ?? (() => new Date());
  const token = leerToken(ctx.query);

  const avisos = crearAvisos();
  const cuerpo = el('div', { class: 'turno-token-cuerpo' });
  contenedor.replaceChildren(el('section', { class: 'turno-token' }, el('h1', {}, TITULOS[accion]), avisos.raiz, cuerpo));

  if (token === null) {
    mostrarLinkInvalido();
    return Promise.resolve();
  }
  const tokenValido = token;
  let enviando = false;

  function mostrarLinkInvalido(): void {
    avisos.limpiar();
    cuerpo.replaceChildren(
      el('h2', {}, 'El link no es válido'),
      el('p', {}, 'Revisá que hayas abierto el link completo del último email del consultorio.'),
    );
  }

  async function leer(): Promise<void> {
    avisos.informar('Cargando tu turno…');
    cuerpo.replaceChildren();
    try {
      const resumen = await obtenerTurnoPorToken(tokenValido);
      avisos.limpiar();
      mostrarTurno(resumen);
    } catch (error) {
      const { mensaje, efecto } = accionDeErrorToken(error, accion);
      if (efecto === 'linkInvalido') {
        mostrarLinkInvalido();
        return;
      }
      const reintentar = crearBoton('Reintentar');
      reintentar.addEventListener('click', () => void leer());
      avisos.fallar(mensaje, reintentar);
    }
  }

  function mostrarTurno(resumen: TurnoResumenPublico): void {
    const vista = vistaDeTurno(resumen, accion, ahora());
    const partes: Node[] = [crearDetalle(resumen)];
    if (vista.tipo === 'informativa') {
      const mensaje = el('div', { role: 'status', class: 'turno-token-mensaje' }, el('p', {}, vista.mensaje));
      if (vista.contacto) {
        mensaje.append(el('p', { class: 'turno-token-contacto' }, `Contacto del consultorio: ${resumen.contactoConsultorio}`));
      }
      if (vista.reservarOtro) {
        mensaje.append(crearEnlaceReservar());
      }
      partes.push(mensaje);
    } else {
      const acciones = el('div', { class: 'turno-token-acciones' });
      const principal = crearBoton(TEXTO_BOTON[accion], { clase: accion === 'cancelar' ? 'turno-token-destructivo' : 'turno-token-principal' });
      acciones.append(principal);
      if (accion === 'confirmar') {
        principal.addEventListener('click', () => void ejecutar(principal));
      } else {
        principal.addEventListener('click', () => pedirConfirmacion(acciones, principal));
      }
      partes.push(acciones);
    }
    cuerpo.replaceChildren(...partes);
  }

  /** Cancelar es irreversible: se pregunta en pantalla (sin modal) antes de enviar nada. */
  function pedirConfirmacion(acciones: HTMLElement, principal: HTMLButtonElement): void {
    const pregunta = el('p', { id: 'turno-token-pregunta', tabindex: '-1' }, '¿Seguro que querés cancelar tu turno?');
    const si = crearBoton('Sí, cancelar turno', { clase: 'turno-token-destructivo' });
    const no = crearBoton('No, mantener turno');
    const grupo = el('div', { role: 'group', 'aria-labelledby': pregunta.id, class: 'turno-token-confirmacion' },
      pregunta, el('div', { class: 'turno-token-botones' }, si, no));
    si.addEventListener('click', () => void ejecutar(si));
    no.addEventListener('click', () => {
      grupo.remove();
      principal.hidden = false;
      principal.focus();
    });
    principal.hidden = true;
    acciones.append(grupo);
    pregunta.focus();
  }

  async function ejecutar(principal: HTMLButtonElement): Promise<void> {
    if (enviando) {
      return;
    }
    enviando = true;
    const textoOriginal = principal.textContent ?? TEXTO_BOTON[accion];
    principal.disabled = true;
    principal.setAttribute('aria-busy', 'true');
    principal.textContent = TEXTO_ENVIANDO[accion];
    try {
      const resumen = await (accion === 'confirmar' ? confirmarTurnoPorToken : cancelarTurnoPorToken)(tokenValido);
      avisos.limpiar();
      mostrarResultado(resumen);
    } catch (error) {
      const { mensaje, efecto } = accionDeErrorToken(error, accion);
      if (efecto === 'linkInvalido') {
        mostrarLinkInvalido();
      } else if (efecto === 'releer') {
        await releer(mensaje);
      } else {
        avisos.fallar(mensaje);
        principal.disabled = false;
        principal.removeAttribute('aria-busy');
        principal.textContent = textoOriginal;
      }
    } finally {
      enviando = false;
    }
  }

  /** Tras un 422 el turno cambio: se vuelve a leer y se muestra el motivo en una alerta junto al estado real. */
  async function releer(mensaje: string): Promise<void> {
    try {
      mostrarTurno(await obtenerTurnoPorToken(tokenValido));
      avisos.fallar(mensaje);
    } catch (error) {
      // No se puede reintentar una accion ya rechazada: se conserva la alerta y se oculta el boton.
      cuerpo.querySelector('.turno-token-acciones')?.remove();
      const siguiente = accionDeErrorToken(error, accion);
      if (siguiente.efecto === 'linkInvalido') {
        mostrarLinkInvalido();
        return;
      }
      avisos.fallar(mensaje);
    }
  }

  function mostrarResultado(resumen: TurnoResumenPublico): void {
    const mensaje = accion === 'confirmar'
      ? `¡Listo, ${resumen.nombrePaciente}! Tu turno quedó confirmado.`
      : 'Tu turno quedó cancelado. El horario quedó libre.';
    const titulo = el('h2', { tabindex: '-1' }, mensaje);
    const resultado = el('div', { role: 'status', class: 'turno-token-resultado' }, titulo);
    if (accion === 'cancelar') {
      resultado.append(crearEnlaceReservar());
    }
    cuerpo.replaceChildren(resultado, crearDetalle(resumen));
    titulo.focus();
  }

  return leer();
}

function crearEnlaceReservar(): HTMLElement {
  return el('p', {}, el('a', { href: '/reservar', class: 'turno-token-enlace' }, 'Reservar otro turno'));
}

function crearDetalle(resumen: TurnoResumenPublico): HTMLElement {
  const fila = (termino: string, valor: string): Node[] => [el('dt', {}, termino), el('dd', {}, valor)];
  return el('dl', { class: 'turno-token-detalle' },
    ...fila('Paciente', resumen.nombrePaciente),
    ...fila('Prestación', `${resumen.prestacion.nombre} (${resumen.prestacion.duracionMinutos} min)`),
    ...fila('Fecha', formatearFechaTurno(resumen.inicio)),
    ...fila('Horario', `${formatearHora(resumen.inicio)} a ${formatearHora(resumen.fin)}`),
    ...fila('Estado', textoEstado(resumen.estado)),
    ...fila('Contacto del consultorio', resumen.contactoConsultorio),
  );
}
