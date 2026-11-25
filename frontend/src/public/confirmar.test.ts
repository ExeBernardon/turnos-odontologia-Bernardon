import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ejemplo } from '../admin/configuracion/ejemplos.testutil';
import { boton, respuesta, stubServidor } from '../admin/configuracion/servidor.testutil';
import type { ContextoRuta } from '../shared/router';
import { iniciarTurnoToken } from './turno-token';

const TOKEN = 'TOKEN-DE-EJEMPLO';
const AHORA = new Date('2026-12-02T15:00:00Z'); // el turno del ejemplo es el 3/12 a las 10:00 (hora de Buenos Aires)
const GET = `GET /api/public/turnos/${TOKEN}`;
const POST = `POST /api/public/turnos/${TOKEN}/confirmar`;

const PENDIENTE = ejemplo('turnos-publico-obtener.200-pendiente') as Record<string, unknown>;
const CONFIRMADO = ejemplo('turnos-publico-obtener.200-confirmado');

let contenedor: HTMLElement;

function ctx(query: string): ContextoRuta {
  return { ruta: '/turno/confirmar', query: new URLSearchParams(query) };
}

async function abrir(query = `token=${TOKEN}`): Promise<void> {
  await iniciarTurnoToken(contenedor, 'confirmar', ctx(query), { ahora: () => AHORA });
}

function texto(): string {
  return contenedor.textContent ?? '';
}

beforeEach(() => {
  contenedor = document.createElement('div');
  document.body.append(contenedor);
});

afterEach(() => {
  contenedor.remove();
  vi.unstubAllGlobals();
});

describe('confirmar - link invalido sin pedidos', () => {
  it.each([['sin token', ''], ['token corto', 'token=abc']])('%s muestra el h1 y "El link no es válido" sin fetch', async (_c, query) => {
    const { llamadas } = stubServidor({});
    await abrir(query);
    expect(contenedor.querySelector('h1')?.textContent).toBe('Confirmar turno');
    expect(texto()).toContain('El link no es válido');
    expect(llamadas).toHaveLength(0);
  });

  it('el h1 esta de forma sincronica, antes de esperar nada', () => {
    stubServidor({ [GET]: () => respuesta(200, PENDIENTE) });
    void iniciarTurnoToken(contenedor, 'confirmar', ctx(`token=${TOKEN}`), { ahora: () => AHORA });
    expect(contenedor.querySelectorAll('h1')).toHaveLength(1);
    expect(contenedor.querySelector('h1')?.textContent).toBe('Confirmar turno');
  });
});

describe('confirmar - abrir el link', () => {
  it('el unico pedido al abrir es el GET y muestra el resumen del ejemplo con el boton', async () => {
    const { llamadas } = stubServidor({ [GET]: () => respuesta(200, PENDIENTE) });
    await abrir();
    expect(llamadas.map((l) => l.metodo + ' ' + l.url)).toEqual([GET]);
    for (const esperado of [
      'Ana',
      'Consulta (30 min)',
      'jueves 3 de diciembre de 2026',
      '10:00 a 10:30',
      'Pendiente de confirmación',
      'Consultorio de ejemplo - Tel. +54 9 11 0000-0000',
    ]) {
      expect(texto()).toContain(esperado);
    }
    expect(boton(contenedor, 'Confirmar asistencia').disabled).toBe(false);
  });

  it('no muestra el token ni datos personales agregados a la respuesta', async () => {
    stubServidor({ [GET]: () => respuesta(200, { ...PENDIENTE, dni: '99999999', email: 'ana@ejemplo.com', telefono: '1155550000' }) });
    await abrir();
    for (const prohibido of [TOKEN, '99999999', 'ana@ejemplo.com', '1155550000']) {
      expect(texto()).not.toContain(prohibido);
    }
  });

  it('el resumen es una lista de definiciones', async () => {
    stubServidor({ [GET]: () => respuesta(200, PENDIENTE) });
    await abrir();
    const terminos = Array.from(contenedor.querySelectorAll('dl dt')).map((t) => t.textContent);
    expect(terminos).toEqual(['Paciente', 'Prestación', 'Fecha', 'Horario', 'Estado', 'Contacto del consultorio']);
  });
});

describe('confirmar - confirmar asistencia', () => {
  const CONFIRMADO_200 = ejemplo('turnos-publico-confirmar.200');

  it('un solo POST aunque se active dos veces, con el boton deshabilitado y ocupado mientras espera', async () => {
    let liberar: (r: Response) => void = () => undefined;
    const { llamadas } = stubServidor({
      [GET]: () => respuesta(200, PENDIENTE),
      [POST]: () => new Promise<Response>((resolver) => { liberar = resolver; }),
    });
    await abrir();
    const b = boton(contenedor, 'Confirmar asistencia');
    b.click();
    expect(b.disabled).toBe(true);
    expect(b.getAttribute('aria-busy')).toBe('true');
    expect(b.textContent).toBe('Confirmando…');
    b.click();
    liberar(respuesta(200, CONFIRMADO_200));
    await vi.waitFor(() => expect(texto()).toContain('Tu turno quedó confirmado'));
    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(1);
  });

  it('con el 200 informa el resultado, muestra el estado Confirmado, quita el boton y enfoca el mensaje dentro de un status', async () => {
    stubServidor({ [GET]: () => respuesta(200, PENDIENTE), [POST]: () => respuesta(200, CONFIRMADO_200) });
    await abrir();
    boton(contenedor, 'Confirmar asistencia').click();
    await vi.waitFor(() => expect(texto()).toContain('¡Listo, Ana! Tu turno quedó confirmado.'));
    expect(texto()).toContain('Confirmado');
    expect(texto()).not.toContain('Pendiente de confirmación');
    expect(Array.from(contenedor.querySelectorAll('button'))).toHaveLength(0);
    const activo = document.activeElement;
    expect(activo?.textContent).toBe('¡Listo, Ana! Tu turno quedó confirmado.');
    expect(activo?.closest('[role="status"]')).not.toBeNull();
  });

  it('el POST no lleva body ni el token en el texto de la pagina', async () => {
    const { llamadas } = stubServidor({ [GET]: () => respuesta(200, PENDIENTE), [POST]: () => respuesta(200, CONFIRMADO_200) });
    await abrir();
    boton(contenedor, 'Confirmar asistencia').click();
    await vi.waitFor(() => expect(texto()).toContain('quedó confirmado'));
    expect(llamadas[1]).toEqual({ metodo: 'POST', url: `/api/public/turnos/${TOKEN}/confirmar`, body: undefined });
    expect(texto()).not.toContain(TOKEN);
  });
});

describe('confirmar - vistas informativas sin boton', () => {
  const sinBoton = (): number => contenedor.querySelectorAll('button').length;

  it('ya confirmado: "Tu turno ya estaba confirmado" y sin boton', async () => {
    stubServidor({ [GET]: () => respuesta(200, CONFIRMADO) });
    await abrir();
    expect(texto()).toContain('Tu turno ya estaba confirmado');
    expect(sinBoton()).toBe(0);
  });

  it('cancelado: mensaje y link "Reservar otro turno" a /reservar', async () => {
    stubServidor({ [GET]: () => respuesta(200, { ...PENDIENTE, estado: 'CANCELADO', puedeConfirmar: false, puedeCancelar: false }) });
    await abrir();
    expect(texto()).toContain('Este turno está cancelado y no se puede confirmar.');
    const enlace = Array.from(contenedor.querySelectorAll('a')).find((a) => a.textContent === 'Reservar otro turno');
    expect(enlace?.getAttribute('href')).toBe('/reservar');
    expect(sinBoton()).toBe(0);
  });

  it('pendiente sin flag y a futuro: "Todavia no se puede confirmar"', async () => {
    stubServidor({ [GET]: () => respuesta(200, { ...PENDIENTE, puedeConfirmar: false }) });
    await abrir();
    expect(texto()).toContain('Todavía no se puede confirmar el turno.');
    expect(sinBoton()).toBe(0);
  });

  it('turno pasado: "Este turno ya pasó."', async () => {
    stubServidor({ [GET]: () => respuesta(200, { ...PENDIENTE, puedeConfirmar: false, estado: 'ASISTIO' }) });
    await abrir();
    expect(texto()).toContain('Este turno ya pasó.');
    expect(sinBoton()).toBe(0);
  });

  it('el mensaje informativo esta dentro de una region status', async () => {
    stubServidor({ [GET]: () => respuesta(200, CONFIRMADO) });
    await abrir();
    expect(contenedor.querySelector('[role="status"].turno-token-mensaje')?.textContent).toContain('Tu turno ya estaba confirmado');
  });
});

describe('confirmar - errores', () => {
  const alerta = (): string => contenedor.querySelector('[role="alert"]')?.textContent ?? '';
  const NO_VALIDO = 'El link no es válido';

  it('404 del GET: "El link no es válido" sin resumen ni boton', async () => {
    stubServidor({ [GET]: () => respuesta(404, ejemplo('turnos-publico-obtener.404-token-invalido')) });
    await abrir();
    expect(texto()).toContain(NO_VALIDO);
    expect(contenedor.querySelector('dl')).toBeNull();
    expect(contenedor.querySelectorAll('button')).toHaveLength(0);
  });

  it('404 del POST: link invalido sin resumen ni boton', async () => {
    stubServidor({ [GET]: () => respuesta(200, PENDIENTE), [POST]: () => respuesta(404, ejemplo('turnos-publico-confirmar.404-token-invalido')) });
    await abrir();
    boton(contenedor, 'Confirmar asistencia').click();
    await vi.waitFor(() => expect(texto()).toContain(NO_VALIDO));
    expect(contenedor.querySelector('dl')).toBeNull();
    expect(contenedor.querySelectorAll('button')).toHaveLength(0);
  });

  it('422 fuera de plazo: alerta fija, segundo GET y vista releida sin boton', async () => {
    const { llamadas } = stubServidor({
      [GET]: [() => respuesta(200, PENDIENTE), () => respuesta(200, { ...PENDIENTE, puedeConfirmar: false })],
      [POST]: () => respuesta(422, ejemplo('turnos-publico-confirmar.422-confirmacion-fuera-de-plazo')),
    });
    await abrir();
    boton(contenedor, 'Confirmar asistencia').click();
    await vi.waitFor(() => expect(contenedor.querySelectorAll('button')).toHaveLength(0));
    expect(llamadas.map((l) => l.metodo)).toEqual(['GET', 'POST', 'GET']);
    expect(alerta()).toBe('Todavía no se puede confirmar el turno. Podés confirmarlo desde 48 h antes, con el link del recordatorio.');
    expect(texto()).not.toContain('Podés confirmarlo desde 48 h antes.'); // texto del servidor
    expect(contenedor.querySelector('dl')).not.toBeNull();
  });

  it('422 TRANSICION_INVALIDA: alerta fija y la vista releida refleja el estado nuevo', async () => {
    stubServidor({
      [GET]: [() => respuesta(200, PENDIENTE), () => respuesta(200, { ...PENDIENTE, estado: 'CANCELADO', puedeConfirmar: false, puedeCancelar: false })],
      [POST]: () => respuesta(422, ejemplo('turnos-publico-confirmar.422-transicion-invalida')),
    });
    await abrir();
    boton(contenedor, 'Confirmar asistencia').click();
    await vi.waitFor(() => expect(texto()).toContain('Este turno está cancelado y no se puede confirmar.'));
    expect(alerta()).toBe('El turno cambió y ya no se puede confirmar desde el link.');
    expect(contenedor.querySelectorAll('button')).toHaveLength(0);
  });

  it('si la relectura tras el 422 falla, conserva la alerta y oculta el boton', async () => {
    stubServidor({
      [GET]: [() => respuesta(200, PENDIENTE), () => respuesta(500, { codigo: 'ERROR_INTERNO' })],
      [POST]: () => respuesta(422, ejemplo('turnos-publico-confirmar.422-confirmacion-fuera-de-plazo')),
    });
    await abrir();
    boton(contenedor, 'Confirmar asistencia').click();
    await vi.waitFor(() => expect(alerta()).toContain('Todavía no se puede confirmar el turno.'));
    await vi.waitFor(() => expect(contenedor.querySelectorAll('button')).toHaveLength(0));
  });

  it('conexion caida en el GET: alerta con "Reintentar" que repite el GET', async () => {
    let falla = true;
    const { llamadas } = stubServidor({
      [GET]: () => {
        if (falla) {
          throw new TypeError('network');
        }
        return respuesta(200, PENDIENTE);
      },
    });
    await abrir();
    expect(alerta()).toBe('No pudimos conectarnos con el servidor. Revisá tu conexión e intentá de nuevo.');
    falla = false;
    boton(contenedor, 'Reintentar').click();
    await vi.waitFor(() => expect(boton(contenedor, 'Confirmar asistencia')).toBeDefined());
    expect(llamadas.filter((l) => l.metodo === 'GET')).toHaveLength(2);
  });

  it('500 en el POST: mensaje generico y boton otra vez habilitado', async () => {
    stubServidor({ [GET]: () => respuesta(200, PENDIENTE), [POST]: () => respuesta(500, { codigo: 'ERROR_INTERNO' }) });
    await abrir();
    boton(contenedor, 'Confirmar asistencia').click();
    await vi.waitFor(() => expect(alerta()).toBe('No se pudo completar la operación. Intentá de nuevo en unos minutos.'));
    const b = boton(contenedor, 'Confirmar asistencia');
    expect(b.disabled).toBe(false);
    expect(b.hasAttribute('aria-busy')).toBe(false);
  });
});

describe('confirmar - accesibilidad estructural', () => {
  it('un solo h1, regiones status y alert, botones nativos type=button y resumen en dl', async () => {
    stubServidor({ [GET]: () => respuesta(200, PENDIENTE) });
    await abrir();
    expect(contenedor.querySelectorAll('h1')).toHaveLength(1);
    expect(contenedor.querySelector('[role="status"]')).not.toBeNull();
    expect(contenedor.querySelector('[role="alert"]')).not.toBeNull();
    const botones = Array.from(contenedor.querySelectorAll('button'));
    expect(botones.length).toBeGreaterThan(0);
    expect(botones.every((b) => b.getAttribute('type') === 'button')).toBe(true);
    expect(contenedor.querySelectorAll('dl dt')).toHaveLength(contenedor.querySelectorAll('dl dd').length);
  });
});
