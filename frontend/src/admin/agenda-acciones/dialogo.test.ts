import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { confirmarAccion } from './dialogo';

let disparador: HTMLButtonElement;
let resto: HTMLElement;

function abrir(): Promise<boolean> {
  return confirmarAccion({
    titulo: 'Cancelar turno',
    descripcion: 'Se cancelará el turno de Bruno Prueba.',
    aceptar: 'Cancelar turno',
    disparador,
  });
}

function dialogo(): HTMLElement | null {
  return document.querySelector<HTMLElement>('[role="alertdialog"]');
}

function botonDe(nombre: string): HTMLButtonElement {
  const boton = [...(dialogo()?.querySelectorAll('button') ?? [])].find((b) => b.textContent === nombre);
  expect(boton).toBeInstanceOf(HTMLButtonElement);
  return boton as HTMLButtonElement;
}

function tecla(destino: Element, key: string, shiftKey = false): KeyboardEvent {
  const evento = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true });
  destino.dispatchEvent(evento);
  return evento;
}

beforeEach(() => {
  document.body.innerHTML = '<main id="resto"><button id="disparador" type="button">Cancelar</button></main>';
  resto = document.getElementById('resto') as HTMLElement;
  disparador = document.getElementById('disparador') as HTMLButtonElement;
  disparador.focus();
});

afterEach(() => {
  document.body.innerHTML = '';
});

describe('confirmarAccion: semántica y foco', () => {
  it('monta un alertdialog modal con título y descripción asociados y foco en "Volver"', () => {
    void abrir();
    const caja = dialogo();
    expect(caja).not.toBeNull();
    expect(caja?.getAttribute('aria-modal')).toBe('true');
    const titulo = document.getElementById(caja?.getAttribute('aria-labelledby') ?? '');
    const descripcion = document.getElementById(caja?.getAttribute('aria-describedby') ?? '');
    expect(titulo?.textContent).toBe('Cancelar turno');
    expect(descripcion?.textContent).toBe('Se cancelará el turno de Bruno Prueba.');
    expect(document.activeElement).toBe(botonDe('Volver'));
  });

  it('"Volver" resuelve false, cierra y devuelve el foco al disparador', async () => {
    const resultado = abrir();
    botonDe('Volver').click();
    expect(await resultado).toBe(false);
    expect(dialogo()).toBeNull();
    expect(document.activeElement).toBe(disparador);
  });

  it('Escape resuelve false y devuelve el foco', async () => {
    const resultado = abrir();
    tecla(botonDe('Volver'), 'Escape');
    expect(await resultado).toBe(false);
    expect(dialogo()).toBeNull();
    expect(document.activeElement).toBe(disparador);
  });

  it('el botón de aceptar resuelve true', async () => {
    const resultado = abrir();
    botonDe('Cancelar turno').click();
    expect(await resultado).toBe(true);
    expect(dialogo()).toBeNull();
  });
});

describe('confirmarAccion: foco atrapado y contenido inerte', () => {
  it('Tab desde el último botón vuelve al primero y Shift+Tab hace lo contrario', () => {
    void abrir();
    const volver = botonDe('Volver');
    const aceptar = botonDe('Cancelar turno');
    aceptar.focus();
    expect(tecla(aceptar, 'Tab').defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(volver);
    expect(tecla(volver, 'Tab', true).defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(aceptar);
  });

  it('Tab entre los dos botones no se intercepta', () => {
    void abrir();
    const volver = botonDe('Volver');
    expect(tecla(volver, 'Tab').defaultPrevented).toBe(false);
  });

  it('marca inert y aria-hidden en el resto mientras está abierto y lo restaura al cerrar', async () => {
    resto.setAttribute('aria-hidden', 'false');
    const resultado = abrir();
    expect(resto.hasAttribute('inert')).toBe(true);
    expect(resto.getAttribute('aria-hidden')).toBe('true');
    expect(dialogo()?.closest('[inert]')).toBeNull();
    botonDe('Volver').click();
    await resultado;
    expect(resto.hasAttribute('inert')).toBe(false);
    expect(resto.getAttribute('aria-hidden')).toBe('false');
  });

  it('un contenido sin aria-hidden previo queda sin el atributo al cerrar', async () => {
    const resultado = abrir();
    botonDe('Volver').click();
    await resultado;
    expect(resto.hasAttribute('aria-hidden')).toBe(false);
  });

  it('abrir uno nuevo con otro abierto no deja dos diálogos y resuelve false el anterior', async () => {
    const primero = abrir();
    const segundo = abrir();
    expect(await primero).toBe(false);
    expect(document.querySelectorAll('[role="alertdialog"]')).toHaveLength(1);
    expect(resto.hasAttribute('inert')).toBe(true);
    botonDe('Cancelar turno').click();
    expect(await segundo).toBe(true);
    expect(document.querySelectorAll('[role="alertdialog"]')).toHaveLength(0);
    expect(resto.hasAttribute('inert')).toBe(false);
  });
});
