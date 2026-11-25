import { el } from '../../shared/dom';
import { detallesPorCampo, mensajeDeError } from './errores';

/** Un control de formulario con su label y su mensaje de error asociado (accesible). */
export interface Campo {
  readonly raiz: HTMLElement;
  readonly control: HTMLInputElement | HTMLSelectElement;
  readonly error: HTMLElement;
}

export function crearCampo(prefijo: string, nombre: string, etiqueta: string, control: HTMLInputElement | HTMLSelectElement): Campo {
  const id = prefijo + '-' + nombre;
  control.id = id;
  control.name = nombre;
  const idError = id + '-error';
  const error = el('p', { id: idError, class: 'cfg-error' });
  control.setAttribute('aria-describedby', idError);
  const raiz = el('div', { class: 'cfg-campo' }, el('label', { for: id }, etiqueta), control, error);
  return { raiz, control, error };
}

export function mostrarErroresCampos(campos: Readonly<Record<string, Campo>>, errores: Readonly<Record<string, string | undefined>>): void {
  let primero: Campo | undefined;
  for (const [nombre, campo] of Object.entries(campos)) {
    const mensaje = errores[nombre];
    campo.error.textContent = mensaje ?? '';
    if (mensaje === undefined) {
      campo.control.removeAttribute('aria-invalid');
    } else {
      campo.control.setAttribute('aria-invalid', 'true');
      primero ??= campo;
    }
  }
  primero?.control.focus();
}

export function limpiarErroresCampos(campos: Readonly<Record<string, Campo>>): void {
  mostrarErroresCampos(campos, {});
}

/**
 * Muestra un error de la API: los `detalles` de un 400 VALIDACION van junto a cada campo; el resto,
 * (y los detalles de campos que el formulario no tiene) va a la alerta general.
 */
export function mostrarErrorServidor(campos: Readonly<Record<string, Campo>>, avisos: Avisos, error: unknown): void {
  const porCampo = detallesPorCampo(error);
  const propios: Record<string, string> = {};
  let huerfanos = false;
  for (const [nombre, mensaje] of Object.entries(porCampo)) {
    if (nombre in campos) {
      propios[nombre] = mensaje;
    } else {
      huerfanos = true;
    }
  }
  if (Object.keys(propios).length > 0) {
    mostrarErroresCampos(campos, propios);
    if (huerfanos) {
      avisos.fallar(mensajeDeError(error));
    }
    return;
  }
  avisos.fallar(mensajeDeError(error));
}

/** Regiones de estado (`role=status`) y de error (`role=alert`) mas un hueco para una accion de recuperacion. */
export interface Avisos {
  readonly raiz: HTMLElement;
  informar(mensaje: string): void;
  fallar(mensaje: string, accion?: HTMLButtonElement): void;
  limpiar(): void;
}

export function crearAvisos(): Avisos {
  const estado = el('p', { role: 'status', class: 'cfg-estado' });
  const alerta = el('p', { role: 'alert', class: 'cfg-alerta' });
  const accion = el('div', { class: 'cfg-alerta-accion' });
  const raiz = el('div', { class: 'cfg-avisos' }, estado, alerta, accion);
  return {
    raiz,
    informar(mensaje) {
      alerta.textContent = '';
      accion.replaceChildren();
      estado.textContent = mensaje;
    },
    fallar(mensaje, boton) {
      estado.textContent = '';
      alerta.textContent = mensaje;
      accion.replaceChildren(...(boton === undefined ? [] : [boton]));
    },
    limpiar() {
      estado.textContent = '';
      alerta.textContent = '';
      accion.replaceChildren();
    },
  };
}

export function crearBoton(texto: string, opciones: { etiqueta?: string; tipo?: 'button' | 'submit'; clase?: string } = {}): HTMLButtonElement {
  const atributos: Record<string, string> = { type: opciones.tipo ?? 'button' };
  if (opciones.etiqueta !== undefined) {
    atributos['aria-label'] = opciones.etiqueta;
  }
  if (opciones.clase !== undefined) {
    atributos['class'] = opciones.clase;
  }
  return el('button', atributos, texto);
}
