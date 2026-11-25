import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ejemplo } from '../admin/configuracion/ejemplos.testutil';
import { boton, respuesta, stubServidor } from '../admin/configuracion/servidor.testutil';
import type { ContextoRuta } from '../shared/router';
import { iniciarTurnoToken } from './turno-token';

const TOKEN = 'TOKEN-DE-EJEMPLO';
const AHORA = new Date('2026-12-01T15:00:00Z'); // 2 dias antes del turno del ejemplo (3/12 10:00 en Buenos Aires)
const GET = `GET /api/public/turnos/${TOKEN}`;
const POST = `POST /api/public/turnos/${TOKEN}/cancelar`;
const CONTACTO = 'Consultorio de ejemplo - Tel. +54 9 11 0000-0000';

const PENDIENTE = ejemplo('turnos-publico-obtener.200-pendiente') as Record<string, unknown>;
const CONFIRMADO = ejemplo('turnos-publico-obtener.200-confirmado');
const CANCELADO_200 = ejemplo('turnos-publico-cancelar.200');
const FUERA_DE_PLAZO = { ...PENDIENTE, puedeConfirmar: true, puedeCancelar: false };

let contenedor: HTMLElement;

async function abrir(query = `token=${TOKEN}`): Promise<void> {
  const ctx: ContextoRuta = { ruta: '/turno/cancelar', query: new URLSearchParams(query) };
  await iniciarTurnoToken(contenedor, 'cancelar', ctx, { ahora: () => AHORA });
}

const texto = (): string => contenedor.textContent ?? '';
const alerta = (): string => contenedor.querySelector('[role="alert"]')?.textContent ?? '';
const botones = (): string[] => Array.from(contenedor.querySelectorAll('button')).filter((b) => !b.hidden).map((b) => b.textContent ?? '');

beforeEach(() => {
  contenedor = document.createElement('div');
  document.body.append(contenedor);
});

afterEach(() => {
  contenedor.remove();
  vi.unstubAllGlobals();
});

describe('cancelar - apertura', () => {
  it.each([['sin token', ''], ['token corto', 'token=abc']])('%s: h1 "Cancelar turno" y "El link no es válido" sin fetch', async (_c, query) => {
    const { llamadas } = stubServidor({});
    await abrir(query);
    expect(contenedor.querySelectorAll('h1')).toHaveLength(1);
    expect(contenedor.querySelector('h1')?.textContent).toBe('Cancelar turno');
    expect(texto()).toContain('El link no es válido');
    expect(llamadas).toHaveLength(0);
  });

  it('con el ejemplo pendiente muestra el resumen y el boton, con un solo GET y ningun POST', async () => {
    const { llamadas } = stubServidor({ [GET]: () => respuesta(200, PENDIENTE) });
    await abrir();
    expect(llamadas.map((l) => l.metodo + ' ' + l.url)).toEqual([GET]);
    expect(contenedor.querySelector('h1')?.textContent).toBe('Cancelar turno');
    expect(texto()).toContain('Ana');
    expect(texto()).toContain('10:00 a 10:30');
    expect(texto()).not.toContain(TOKEN);
    expect(botones()).toEqual(['Cancelar turno']);
  });
});

describe('cancelar - confirmacion en pantalla', () => {
  it('"Cancelar turno" no hace POST: pregunta, enfoca la pregunta y ofrece Si / No', async () => {
    const { llamadas } = stubServidor({ [GET]: () => respuesta(200, PENDIENTE) });
    await abrir();
    boton(contenedor, 'Cancelar turno').click();
    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(0);
    const pregunta = contenedor.querySelector('[role="group"] p');
    expect(pregunta?.textContent).toBe('¿Seguro que querés cancelar tu turno?');
    expect(document.activeElement).toBe(pregunta);
    expect(botones()).toEqual(['Sí, cancelar turno', 'No, mantener turno']);
    const grupo = contenedor.querySelector('[role="group"]');
    expect(grupo?.getAttribute('aria-labelledby')).toBe(pregunta?.id);
  });

  it('"No, mantener turno" oculta la pregunta, no hace POST y devuelve el foco a "Cancelar turno"', async () => {
    const { llamadas } = stubServidor({ [GET]: () => respuesta(200, PENDIENTE) });
    await abrir();
    boton(contenedor, 'Cancelar turno').click();
    boton(contenedor, 'No, mantener turno').click();
    expect(contenedor.querySelector('[role="group"]')).toBeNull();
    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(0);
    expect(botones()).toEqual(['Cancelar turno']);
    expect(document.activeElement).toBe(boton(contenedor, 'Cancelar turno'));
  });
});

describe('cancelar - confirmar la cancelacion', () => {
  async function hastaLaPregunta(): Promise<void> {
    await abrir();
    boton(contenedor, 'Cancelar turno').click();
  }

  it('"Sí" hace un unico POST aunque se active dos veces (Cancelando…)', async () => {
    let liberar: (r: Response) => void = () => undefined;
    const { llamadas } = stubServidor({
      [GET]: () => respuesta(200, PENDIENTE),
      [POST]: () => new Promise<Response>((resolver) => { liberar = resolver; }),
    });
    await hastaLaPregunta();
    const si = boton(contenedor, 'Sí, cancelar turno');
    si.click();
    expect(si.disabled).toBe(true);
    expect(si.textContent).toBe('Cancelando…');
    expect(si.getAttribute('aria-busy')).toBe('true');
    si.click();
    liberar(respuesta(200, CANCELADO_200));
    await vi.waitFor(() => expect(texto()).toContain('Tu turno quedó cancelado.'));
    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(1);
  });

  it('con el 200: resultado enfocado en un status, estado Cancelado, link a /reservar y sin botones', async () => {
    stubServidor({ [GET]: () => respuesta(200, PENDIENTE), [POST]: () => respuesta(200, CANCELADO_200) });
    await hastaLaPregunta();
    boton(contenedor, 'Sí, cancelar turno').click();
    await vi.waitFor(() => expect(texto()).toContain('Tu turno quedó cancelado. El horario quedó libre.'));
    expect(texto()).toContain('Cancelado');
    expect(botones()).toEqual([]);
    const enlace = Array.from(contenedor.querySelectorAll('a')).find((a) => a.textContent === 'Reservar otro turno');
    expect(enlace?.getAttribute('href')).toBe('/reservar');
    expect(document.activeElement?.textContent).toBe('Tu turno quedó cancelado. El horario quedó libre.');
    expect(document.activeElement?.closest('[role="status"]')).not.toBeNull();
    expect(texto()).not.toContain(TOKEN);
  });
});

describe('cancelar - vistas informativas', () => {
  it('cancelado: "Tu turno ya estaba cancelado." con link a reservar y sin boton', async () => {
    stubServidor({ [GET]: () => respuesta(200, CANCELADO_200) });
    await abrir();
    expect(texto()).toContain('Tu turno ya estaba cancelado.');
    expect(contenedor.querySelector('a[href="/reservar"]')).not.toBeNull();
    expect(botones()).toEqual([]);
  });

  it('confirmado: pide comunicarse y muestra el contacto', async () => {
    stubServidor({ [GET]: () => respuesta(200, CONFIRMADO) });
    await abrir();
    const mensaje = contenedor.querySelector('.turno-token-mensaje')?.textContent ?? '';
    expect(mensaje).toContain('Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio.');
    expect(mensaje).toContain(CONTACTO);
    expect(botones()).toEqual([]);
  });

  it('pendiente fuera de plazo (< 24 h): mensaje y contacto, sin boton', async () => {
    stubServidor({ [GET]: () => respuesta(200, FUERA_DE_PLAZO) });
    await abrir();
    const mensaje = contenedor.querySelector('.turno-token-mensaje')?.textContent ?? '';
    expect(mensaje).toContain('Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio.');
    expect(mensaje).toContain(CONTACTO);
    expect(botones()).toEqual([]);
  });

  it('el flag del backend manda aunque el turno sea futuro y pendiente', async () => {
    stubServidor({ [GET]: () => respuesta(200, { ...PENDIENTE, puedeCancelar: false }) });
    await abrir();
    expect(botones()).toEqual([]);
  });

  it('turno pasado: "Este turno ya pasó." sin contacto ni boton', async () => {
    stubServidor({ [GET]: () => respuesta(200, { ...PENDIENTE, puedeCancelar: false, estado: 'AUSENTE' }) });
    await abrir();
    expect(contenedor.querySelector('.turno-token-mensaje')?.textContent).toBe('Este turno ya pasó.');
    expect(botones()).toEqual([]);
  });
});

describe('cancelar - errores del POST', () => {
  async function hastaElSi(posts: Array<() => Response>, gets: Array<() => Response>): Promise<{ llamadas: { metodo: string }[] }> {
    const { llamadas } = stubServidor({ [GET]: gets, [POST]: posts });
    await abrir();
    boton(contenedor, 'Cancelar turno').click();
    boton(contenedor, 'Sí, cancelar turno').click();
    return { llamadas };
  }

  it('422 CANCELACION_FUERA_DE_PLAZO: alerta fija, relectura y contacto', async () => {
    const { llamadas } = await hastaElSi(
      [() => respuesta(422, ejemplo('turnos-publico-cancelar.422-cancelacion-fuera-de-plazo'))],
      [() => respuesta(200, PENDIENTE), () => respuesta(200, FUERA_DE_PLAZO)],
    );
    await vi.waitFor(() => expect(botones()).toEqual([]));
    expect(llamadas.map((l) => l.metodo)).toEqual(['GET', 'POST', 'GET']);
    expect(alerta()).toBe('Ya no se puede cancelar desde el link (faltan menos de 24 h). Comunicate con el consultorio.');
    expect(texto()).toContain(CONTACTO);
  });

  it('422 TRANSICION_INVALIDA: alerta fija y el mensaje del estado nuevo', async () => {
    await hastaElSi(
      [() => respuesta(422, ejemplo('turnos-publico-cancelar.422-transicion-invalida'))],
      [() => respuesta(200, PENDIENTE), () => respuesta(200, CONFIRMADO)],
    );
    await vi.waitFor(() => expect(texto()).toContain('Tu turno ya está confirmado. Para cancelarlo comunicate con el consultorio.'));
    expect(alerta()).toBe('El turno cambió y ya no se puede cancelar desde el link.');
    expect(alerta()).not.toContain('Tu turno ya está confirmado');
    expect(botones()).toEqual([]);
  });

  it('404: link invalido sin resumen ni botones', async () => {
    await hastaElSi([() => respuesta(404, ejemplo('turnos-publico-cancelar.404-token-invalido'))], [() => respuesta(200, PENDIENTE)]);
    await vi.waitFor(() => expect(texto()).toContain('El link no es válido'));
    expect(contenedor.querySelector('dl')).toBeNull();
    expect(botones()).toEqual([]);
  });

  it('500: mensaje generico y el boton "Sí, cancelar turno" vuelve a estar habilitado', async () => {
    await hastaElSi([() => respuesta(500, { codigo: 'ERROR_INTERNO' })], [() => respuesta(200, PENDIENTE)]);
    await vi.waitFor(() => expect(alerta()).toBe('No se pudo completar la operación. Intentá de nuevo en unos minutos.'));
    const si = boton(contenedor, 'Sí, cancelar turno');
    expect(si.disabled).toBe(false);
    expect(si.hasAttribute('aria-busy')).toBe(false);
  });

  it('conexion caida en el POST: mensaje de conexion y se puede reintentar', async () => {
    const { llamadas } = stubServidor({
      [GET]: () => respuesta(200, PENDIENTE),
      [POST]: [() => { throw new TypeError('network'); }, () => respuesta(200, CANCELADO_200)],
    });
    await abrir();
    boton(contenedor, 'Cancelar turno').click();
    boton(contenedor, 'Sí, cancelar turno').click();
    await vi.waitFor(() => expect(alerta()).toContain('No pudimos conectarnos'));
    boton(contenedor, 'Sí, cancelar turno').click();
    await vi.waitFor(() => expect(texto()).toContain('Tu turno quedó cancelado.'));
    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(2);
  });
});

describe('cancelar - accesibilidad estructural', () => {
  it('un solo h1, regiones status y alert, botones nativos type=button (tambien los de la pregunta) y dl', async () => {
    stubServidor({ [GET]: () => respuesta(200, PENDIENTE) });
    await abrir();
    boton(contenedor, 'Cancelar turno').click();
    expect(contenedor.querySelectorAll('h1')).toHaveLength(1);
    expect(contenedor.querySelector('[role="status"]')).not.toBeNull();
    expect(contenedor.querySelector('[role="alert"]')).not.toBeNull();
    const todos = Array.from(contenedor.querySelectorAll('button'));
    expect(todos).toHaveLength(3);
    expect(todos.every((b) => b.getAttribute('type') === 'button')).toBe(true);
    expect(contenedor.querySelectorAll('dl dt')).toHaveLength(6);
  });

  it('el boton de cancelar se marca como destructivo ademas de su texto', async () => {
    stubServidor({ [GET]: () => respuesta(200, PENDIENTE) });
    await abrir();
    expect(boton(contenedor, 'Cancelar turno').classList.contains('turno-token-destructivo')).toBe(true);
  });
});
