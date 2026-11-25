import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ejemplo } from '../admin/configuracion/ejemplos.testutil';
import { boton, campoPorLabel, respuesta, stubServidor } from '../admin/configuracion/servidor.testutil';
import { iniciar } from './reservar';

const PRESTACIONES = ejemplo('prestaciones-publicas-listar.200');
const REQUEST = ejemplo('turnos-publico-reservar.request');
const RESUMEN = ejemplo('turnos-publico-reservar.201') as Record<string, unknown>;
const AHORA = new Date('2026-11-30T15:00:00Z'); // 12:00 en Buenos Aires: ventana 2026-11-30 .. 2027-01-29

const GET_PRESTACIONES = 'GET /api/public/prestaciones';
const POST_TURNOS = 'POST /api/public/turnos';

function getDisponibilidad(fecha: string, prestacionId = 1): string {
  return `GET /api/public/disponibilidad?prestacionId=${prestacionId}&desde=${fecha}&hasta=${fecha}`;
}

function disponibilidad(fecha: string, horarios: string[]): unknown {
  return { prestacion: { id: 1, nombre: 'Consulta', duracionMinutos: 30 }, dias: [{ fecha, horarios }] };
}

const HORARIOS_DICIEMBRE_10 = [
  '2026-12-10T09:30:00-03:00',
  '2026-12-10T10:00:00-03:00',
  '2026-12-10T10:30:00-03:00',
];

let contenedor: HTMLElement;

async function cargar(): Promise<void> {
  await iniciar(contenedor, { ahora: () => AHORA });
}

function porLabel(texto: string): HTMLInputElement {
  const label = Array.from(contenedor.querySelectorAll('label')).find((l) => l.textContent === texto);
  const control = label?.control;
  if (!(control instanceof HTMLInputElement)) {
    throw new Error('opcion no encontrada: ' + texto);
  }
  return control;
}

function oculto(nodo: Element): boolean {
  return nodo.closest('[hidden]') !== null;
}

function elegirPrestacion(texto: string): void {
  porLabel(texto).click();
}

function escribirFecha(valor: string): void {
  const campo = campoPorLabel(contenedor, 'Fecha');
  campo.value = valor;
  campo.dispatchEvent(new Event('change', { bubbles: true }));
}

function elegirHorario(hora: string): void {
  porLabel(hora).click();
}

function escribir(label: string, valor: string): void {
  campoPorLabel(contenedor, label).value = valor;
}

function casilla(): HTMLInputElement {
  const c = contenedor.querySelector<HTMLInputElement>('input[type="checkbox"]');
  if (c === null) {
    throw new Error('casilla no encontrada');
  }
  return c;
}

function completarDatos(): void {
  escribir('Nombre', 'Ana');
  escribir('Apellido', 'Ejemplo');
  escribir('DNI', '99.999.999');
  escribir('Email', 'paciente@ejemplo.com');
  escribir('Teléfono', '+54 9 11 0000-0000');
  casilla().checked = true;
}

function enviar(): void {
  contenedor.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

function textoDe(id: string): string {
  return contenedor.querySelector('#' + id)?.textContent ?? '';
}

function alerta(): string {
  return contenedor.querySelector('.cfg-avisos [role="alert"]')?.textContent ?? '';
}

function cantidad(llamadas: { metodo: string; url: string }[], metodo: string, inicio: string): number {
  return llamadas.filter((l) => l.metodo === metodo && l.url.startsWith(inicio)).length;
}

/** Llega hasta el formulario de datos visible: prestacion 1, fecha 2026-12-10, horario de las 10:00. */
async function llegarAlFormulario(): Promise<void> {
  await cargar();
  elegirPrestacion('Consulta (30 min)');
  escribirFecha('2026-12-10');
  await vi.waitFor(() => porLabel('10:00'));
  elegirHorario('10:00');
}

function rutasBase(extra: Record<string, Parameters<typeof stubServidor>[0][string]> = {}): Parameters<typeof stubServidor>[0] {
  return {
    [GET_PRESTACIONES]: () => respuesta(200, PRESTACIONES),
    [getDisponibilidad('2026-12-10')]: () => respuesta(200, disponibilidad('2026-12-10', HORARIOS_DICIEMBRE_10)),
    ...extra,
  };
}

beforeEach(() => {
  contenedor = document.createElement('div');
  document.body.replaceChildren(contenedor);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('reservar: prestaciones', () => {
  it('muestra un unico h1, pide las prestaciones y ofrece radios dentro de un fieldset con leyenda', async () => {
    const { llamadas } = stubServidor(rutasBase());
    await cargar();
    expect(contenedor.querySelectorAll('h1')).toHaveLength(1);
    expect(contenedor.querySelector('h1')?.textContent).toBe('Reservar turno');
    expect(llamadas.map((l) => l.metodo + ' ' + l.url)).toEqual([GET_PRESTACIONES]);
    const consulta = porLabel('Consulta (30 min)');
    expect(consulta.type).toBe('radio');
    expect(porLabel('Limpieza (45 min)').type).toBe('radio');
    expect(consulta.closest('fieldset')?.querySelector('legend')?.textContent).toBe('Prestación');
  });

  it('no muestra fecha, horarios ni datos hasta elegir', async () => {
    stubServidor(rutasBase());
    await cargar();
    expect(oculto(campoPorLabel(contenedor, 'Fecha'))).toBe(true);
    expect(oculto(contenedor.querySelector('form') ?? contenedor)).toBe(true);
  });

  it('con lista vacia informa y no ofrece elegir fecha', async () => {
    stubServidor({ [GET_PRESTACIONES]: () => respuesta(200, []) });
    await cargar();
    expect(contenedor.textContent).toContain('Por ahora no hay prestaciones disponibles para reservar.');
    expect(contenedor.querySelectorAll('input[type="radio"]')).toHaveLength(0);
    expect(oculto(campoPorLabel(contenedor, 'Fecha'))).toBe(true);
  });

  it('ante un error de conexion muestra alerta con Reintentar que vuelve a pedir la lista', async () => {
    const { llamadas } = stubServidor({
      [GET_PRESTACIONES]: [
        () => {
          throw new Error('sin red');
        },
        () => respuesta(200, PRESTACIONES),
      ],
    });
    await cargar();
    expect(alerta()).toMatch(/conectarnos/i);
    boton(contenedor, 'Reintentar').click();
    await vi.waitFor(() => expect(porLabel('Consulta (30 min)')).toBeTruthy());
    expect(llamadas).toHaveLength(2);
    expect(alerta()).toBe('');
  });
});

describe('reservar: fecha y horarios', () => {
  it('al elegir prestacion muestra la fecha con min y max de la ventana', async () => {
    stubServidor(rutasBase());
    await cargar();
    elegirPrestacion('Consulta (30 min)');
    const fecha = campoPorLabel(contenedor, 'Fecha');
    expect(oculto(fecha)).toBe(false);
    expect(fecha.getAttribute('type')).toBe('date');
    expect(fecha.getAttribute('min')).toBe('2026-11-30');
    expect(fecha.getAttribute('max')).toBe('2027-01-29');
  });

  it('una fecha fuera de ventana o vacia da error junto al campo y no pide disponibilidad', async () => {
    const { llamadas } = stubServidor(rutasBase());
    await cargar();
    elegirPrestacion('Consulta (30 min)');
    escribirFecha('2027-03-01');
    expect(textoDe('res-fecha-error')).not.toBe('');
    expect(campoPorLabel(contenedor, 'Fecha').getAttribute('aria-invalid')).toBe('true');
    escribirFecha('2026-11-29');
    expect(textoDe('res-fecha-error')).not.toBe('');
    boton(contenedor, 'Ver horarios').click();
    expect(textoDe('res-fecha-error')).not.toBe('');
    expect(cantidad(llamadas, 'GET', '/api/public/disponibilidad')).toBe(0);
  });

  it('con fecha valida pide el dia y muestra los horarios en un fieldset con leyenda', async () => {
    const { llamadas } = stubServidor(rutasBase());
    await cargar();
    elegirPrestacion('Consulta (30 min)');
    escribirFecha('2026-12-10');
    await vi.waitFor(() => expect(porLabel('10:00')).toBeTruthy());
    expect(llamadas.at(-1)?.url).toBe('/api/public/disponibilidad?prestacionId=1&desde=2026-12-10&hasta=2026-12-10');
    expect(['09:30', '10:00', '10:30'].map((h) => porLabel(h).type)).toEqual(['radio', 'radio', 'radio']);
    expect(porLabel('10:00').closest('fieldset')?.querySelector('legend')?.textContent).toBe('Horarios disponibles');
    expect(oculto(contenedor.querySelector('form') ?? contenedor)).toBe(true);
  });

  it('el boton Ver horarios tambien pide la disponibilidad', async () => {
    const { llamadas } = stubServidor(rutasBase());
    await cargar();
    elegirPrestacion('Consulta (30 min)');
    campoPorLabel(contenedor, 'Fecha').value = '2026-12-10';
    boton(contenedor, 'Ver horarios').click();
    await vi.waitFor(() => expect(porLabel('09:30')).toBeTruthy());
    expect(cantidad(llamadas, 'GET', '/api/public/disponibilidad')).toBe(1);
  });

  it('un dia sin horarios muestra un estado (no un error) y no el formulario', async () => {
    stubServidor(rutasBase({ [getDisponibilidad('2026-12-02')]: () => respuesta(200, ejemplo('disponibilidad-publica.200')) }));
    await cargar();
    elegirPrestacion('Consulta (30 min)');
    escribirFecha('2026-12-02');
    await vi.waitFor(() => expect(textoDe('res-horarios-estado')).toContain('No hay horarios disponibles ese día. Probá con otra fecha.'));
    expect(contenedor.querySelector('#res-horarios-estado')?.getAttribute('role')).toBe('status');
    expect(alerta()).toBe('');
    expect(contenedor.querySelectorAll('input[name="horario"]')).toHaveLength(0);
    expect(oculto(contenedor.querySelector('form') ?? contenedor)).toBe(true);
  });

  it('cambiar de prestacion descarta fecha y horario y oculta el formulario conservando lo escrito', async () => {
    stubServidor(rutasBase());
    await llegarAlFormulario();
    completarDatos();
    expect(oculto(contenedor.querySelector('form') ?? contenedor)).toBe(false);
    elegirPrestacion('Limpieza (45 min)');
    expect(oculto(contenedor.querySelector('form') ?? contenedor)).toBe(true);
    expect(contenedor.querySelectorAll('input[name="horario"]')).toHaveLength(0);
    expect(campoPorLabel(contenedor, 'Fecha').value).toBe('');
    expect(campoPorLabel(contenedor, 'Nombre').value).toBe('Ana');
    expect(campoPorLabel(contenedor, 'Email').value).toBe('paciente@ejemplo.com');
  });

  it('cambiar de fecha descarta el horario elegido y oculta el formulario conservando lo escrito', async () => {
    stubServidor(rutasBase({ [getDisponibilidad('2026-12-11')]: () => respuesta(200, disponibilidad('2026-12-11', ['2026-12-11T11:00:00-03:00'])) }));
    await llegarAlFormulario();
    completarDatos();
    escribirFecha('2026-12-11');
    expect(oculto(contenedor.querySelector('form') ?? contenedor)).toBe(true);
    await vi.waitFor(() => expect(porLabel('11:00').checked).toBe(false));
    expect(campoPorLabel(contenedor, 'DNI').value).toBe('99.999.999');
  });

  it('descarta una respuesta de disponibilidad que llega fuera de orden', async () => {
    let soltarLenta: () => void = () => undefined;
    const lenta = new Promise<void>((resolver) => {
      soltarLenta = resolver;
    });
    stubServidor(
      rutasBase({
        [getDisponibilidad('2026-12-11')]: async () => {
          await lenta;
          return respuesta(200, disponibilidad('2026-12-11', ['2026-12-11T08:00:00-03:00']));
        },
        [getDisponibilidad('2026-12-12')]: () => respuesta(200, disponibilidad('2026-12-12', ['2026-12-12T15:00:00-03:00'])),
      }),
    );
    await cargar();
    elegirPrestacion('Consulta (30 min)');
    escribirFecha('2026-12-11');
    escribirFecha('2026-12-12');
    await vi.waitFor(() => expect(porLabel('15:00')).toBeTruthy());
    soltarLenta();
    await new Promise((r) => setTimeout(r, 20));
    expect(contenedor.querySelectorAll('input[name="horario"]')).toHaveLength(1);
    expect(contenedor.textContent).not.toContain('08:00');
  });

  it('si la prestacion ya no esta activa avisa y vuelve a cargar la lista', async () => {
    const { llamadas } = stubServidor(
      rutasBase({ [getDisponibilidad('2026-12-10')]: () => respuesta(422, ejemplo('disponibilidad-publica.422-prestacion-inactiva')) }),
    );
    await cargar();
    elegirPrestacion('Consulta (30 min)');
    escribirFecha('2026-12-10');
    await vi.waitFor(() => expect(alerta()).toContain('La prestación elegida ya no está disponible. Elegí otra.'));
    await vi.waitFor(() => expect(cantidad(llamadas, 'GET', '/api/public/prestaciones')).toBe(2));
    expect(porLabel('Consulta (30 min)').checked).toBe(false);
    expect(oculto(campoPorLabel(contenedor, 'Fecha'))).toBe(true);
  });

  it('ante un 404 al pedir horarios tambien recarga las prestaciones', async () => {
    const { llamadas } = stubServidor(
      rutasBase({ [getDisponibilidad('2026-12-10')]: () => respuesta(404, ejemplo('error.404-recurso-no-encontrado')) }),
    );
    await cargar();
    elegirPrestacion('Consulta (30 min)');
    escribirFecha('2026-12-10');
    await vi.waitFor(() => expect(cantidad(llamadas, 'GET', '/api/public/prestaciones')).toBe(2));
    await vi.waitFor(() => expect(alerta()).toContain('ya no está disponible'));
  });
});

describe('reservar: formulario', () => {
  it('muestra los campos con label, atributos de teclado y la casilla desmarcada con su finalidad', async () => {
    stubServidor(rutasBase());
    await llegarAlFormulario();
    const form = contenedor.querySelector('form');
    expect(oculto(form ?? contenedor)).toBe(false);
    expect(form?.hasAttribute('novalidate')).toBe(true);
    const esperados: [string, Record<string, string>][] = [
      ['Nombre', { autocomplete: 'given-name', maxlength: '100' }],
      ['Apellido', { autocomplete: 'family-name', maxlength: '100' }],
      ['DNI', { inputmode: 'numeric', autocomplete: 'off', type: 'text' }],
      ['Email', { type: 'email', autocomplete: 'email', maxlength: '150' }],
      ['Teléfono', { type: 'tel', autocomplete: 'tel' }],
    ];
    for (const [label, atributos] of esperados) {
      const campo = campoPorLabel(contenedor, label);
      for (const [nombre, valor] of Object.entries(atributos)) {
        expect(campo.getAttribute(nombre), `${label}.${nombre}`).toBe(valor);
      }
      const idError = campo.getAttribute('aria-describedby') ?? '';
      expect(contenedor.querySelector('#' + idError)).not.toBeNull();
    }
    expect(casilla().checked).toBe(false);
    const etiqueta = casilla().labels?.[0]?.textContent ?? '';
    expect(etiqueta).toContain('Ley 25.326');
    expect(etiqueta).toContain('gestionar este turno');
    expect(boton(contenedor, 'Reservar').type).toBe('submit');
  });

  it('con datos invalidos muestra errores por campo, enfoca el primero y no envia', async () => {
    const { llamadas } = stubServidor(rutasBase());
    await llegarAlFormulario();
    completarDatos();
    escribir('DNI', '99-999-999');
    escribir('Email', 'paciente@');
    escribir('Teléfono', 'abc');
    enviar();
    for (const label of ['DNI', 'Email', 'Teléfono']) {
      const campo = campoPorLabel(contenedor, label);
      expect(campo.getAttribute('aria-invalid'), label).toBe('true');
      expect(contenedor.querySelector('#' + (campo.getAttribute('aria-describedby') ?? ''))?.textContent, label).not.toBe('');
    }
    expect(campoPorLabel(contenedor, 'Nombre').hasAttribute('aria-invalid')).toBe(false);
    expect(document.activeElement).toBe(campoPorLabel(contenedor, 'DNI'));
    expect(cantidad(llamadas, 'POST', '/api/public/turnos')).toBe(0);
  });

  it('sin marcar la casilla muestra el error junto a ella y no envia', async () => {
    const { llamadas } = stubServidor(rutasBase());
    await llegarAlFormulario();
    completarDatos();
    casilla().checked = false;
    enviar();
    expect(casilla().getAttribute('aria-invalid')).toBe('true');
    expect(textoDe(casilla().getAttribute('aria-describedby') ?? '')).not.toBe('');
    expect(document.activeElement).toBe(casilla());
    expect(cantidad(llamadas, 'POST', '/api/public/turnos')).toBe(0);
  });

  it('un envio valido manda exactamente el body del ejemplo del contrato', async () => {
    const { llamadas } = stubServidor(rutasBase({ [POST_TURNOS]: () => respuesta(201, RESUMEN) }));
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(cantidad(llamadas, 'POST', '/api/public/turnos')).toBe(1));
    expect(llamadas.find((l) => l.metodo === 'POST')?.body).toEqual(REQUEST);
  });

  it('dos activaciones seguidas envian una sola vez y el boton queda deshabilitado con Reservando…', async () => {
    let soltar: () => void = () => undefined;
    const espera = new Promise<void>((resolver) => {
      soltar = resolver;
    });
    const { llamadas } = stubServidor(
      rutasBase({
        [POST_TURNOS]: async () => {
          await espera;
          return respuesta(201, RESUMEN);
        },
      }),
    );
    await llegarAlFormulario();
    completarDatos();
    enviar();
    enviar();
    const reservando = boton(contenedor, 'Reservando…');
    expect(reservando.disabled).toBe(true);
    expect(reservando.getAttribute('aria-busy')).toBe('true');
    soltar();
    await vi.waitFor(() => expect(contenedor.querySelector('h2[tabindex="-1"]')).not.toBeNull());
    expect(cantidad(llamadas, 'POST', '/api/public/turnos')).toBe(1);
  });
});

describe('reservar: errores al enviar', () => {
  it('409 HORARIO_OCUPADO avisa, refresca el dia, descarta el horario y conserva los datos', async () => {
    const { llamadas } = stubServidor(
      rutasBase({ [POST_TURNOS]: () => respuesta(409, ejemplo('turnos-publico-reservar.409-horario-ocupado')) }),
    );
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(alerta()).toBe('Ese horario se acaba de ocupar, elegí otro.'));
    await vi.waitFor(() => expect(cantidad(llamadas, 'GET', '/api/public/disponibilidad')).toBe(2));
    await vi.waitFor(() => expect(porLabel('10:00').checked).toBe(false));
    expect(oculto(contenedor.querySelector('form') ?? contenedor)).toBe(true);
    expect(document.activeElement).toBe(contenedor.querySelector('#res-horarios'));
    elegirHorario('10:30');
    expect(campoPorLabel(contenedor, 'Nombre').value).toBe('Ana');
    expect(campoPorLabel(contenedor, 'DNI').value).toBe('99.999.999');
    expect(campoPorLabel(contenedor, 'Email').value).toBe('paciente@ejemplo.com');
    expect(campoPorLabel(contenedor, 'Teléfono').value).toBe('+54 9 11 0000-0000');
    expect(casilla().checked).toBe(true);
    expect(boton(contenedor, 'Reservar').disabled).toBe(false);
  });

  it('422 HORARIO_NO_DISPONIBLE muestra su mensaje y refresca los horarios', async () => {
    const { llamadas } = stubServidor(
      rutasBase({ [POST_TURNOS]: () => respuesta(422, ejemplo('turnos-publico-reservar.422-horario-no-disponible')) }),
    );
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(alerta()).toBe('Ese horario ya no está disponible. Elegí otro.'));
    await vi.waitFor(() => expect(cantidad(llamadas, 'GET', '/api/public/disponibilidad')).toBe(2));
  });

  it('si al refrescar el dia queda vacio, muestra el estado sin horarios', async () => {
    stubServidor(
      rutasBase({
        [getDisponibilidad('2026-12-10')]: [
          () => respuesta(200, disponibilidad('2026-12-10', HORARIOS_DICIEMBRE_10)),
          () => respuesta(200, disponibilidad('2026-12-10', [])),
        ],
        [POST_TURNOS]: () => respuesta(409, ejemplo('turnos-publico-reservar.409-horario-ocupado')),
      }),
    );
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(textoDe('res-horarios-estado')).toContain('No hay horarios disponibles'));
    expect(document.activeElement).toBe(contenedor.querySelector('#res-horarios-estado'));
  });

  it('422 CONSENTIMIENTO_REQUERIDO marca la casilla', async () => {
    stubServidor(rutasBase({ [POST_TURNOS]: () => respuesta(422, ejemplo('turnos-publico-reservar.422-consentimiento-requerido')) }));
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(casilla().getAttribute('aria-invalid')).toBe('true'));
    expect(textoDe(casilla().getAttribute('aria-describedby') ?? '')).not.toBe('');
    expect(document.activeElement).toBe(casilla());
  });

  it('422 PRESTACION_INACTIVA avisa y recarga las prestaciones', async () => {
    const { llamadas } = stubServidor(
      rutasBase({ [POST_TURNOS]: () => respuesta(422, ejemplo('turnos-publico-reservar.422-prestacion-inactiva')) }),
    );
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(cantidad(llamadas, 'GET', '/api/public/prestaciones')).toBe(2));
    await vi.waitFor(() => expect(alerta()).toContain('La prestación elegida ya no está disponible. Elegí otra.'));
    expect(oculto(contenedor.querySelector('form') ?? contenedor)).toBe(true);
    expect(campoPorLabel(contenedor, 'Nombre').value).toBe('Ana');
  });

  it('400 VALIDACION con detalle de email lo muestra junto al campo con foco', async () => {
    stubServidor(rutasBase({ [POST_TURNOS]: () => respuesta(400, ejemplo('error.400-validacion')) }));
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(campoPorLabel(contenedor, 'Email').getAttribute('aria-invalid')).toBe('true'));
    expect(textoDe('res-email-error')).toBe('El email es obligatorio.');
    expect(textoDe('res-dni-error')).toBe('El DNI debe tener 7 u 8 dígitos.');
    expect(document.activeElement).toBe(campoPorLabel(contenedor, 'DNI'));
  });

  it('un detalle sin campo propio va a la alerta general', async () => {
    const cuerpo = {
      ...(ejemplo('error.400-validacion') as Record<string, unknown>),
      detalles: [{ campo: 'prestacionId', mensaje: 'Es obligatorio.' }],
    };
    stubServidor(rutasBase({ [POST_TURNOS]: () => respuesta(400, cuerpo) }));
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(alerta()).not.toBe(''));
    expect(contenedor.querySelectorAll('[aria-invalid="true"]')).toHaveLength(0);
  });

  it('un 500 muestra un mensaje generico sin texto tecnico y conserva los datos', async () => {
    stubServidor(rutasBase({ [POST_TURNOS]: () => respuesta(500, ejemplo('error.500-error-interno')) }));
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(alerta()).toBe('No se pudo completar la operación. Intentá de nuevo en unos minutos.'));
    expect(campoPorLabel(contenedor, 'Nombre').value).toBe('Ana');
    expect(casilla().checked).toBe(true);
    expect(boton(contenedor, 'Reservar').disabled).toBe(false);
  });

  it('con la conexion caida avisa y permite reintentar', async () => {
    stubServidor(
      rutasBase({
        [POST_TURNOS]: [
          () => {
            throw new Error('sin red');
          },
          () => respuesta(201, RESUMEN),
        ],
      }),
    );
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(alerta()).toMatch(/conectarnos/i));
    expect(campoPorLabel(contenedor, 'Email').value).toBe('paciente@ejemplo.com');
    enviar();
    await vi.waitFor(() => expect(contenedor.querySelector('h2[tabindex="-1"]')).not.toBeNull());
  });
});

describe('reservar: resumen', () => {
  it('tras el 201 reemplaza los pasos por el resumen del turno pendiente, con foco en su h2', async () => {
    stubServidor(rutasBase({ [POST_TURNOS]: () => respuesta(201, RESUMEN) }));
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(contenedor.querySelector('h2[tabindex="-1"]')).not.toBeNull());
    const titulo = contenedor.querySelector('h2[tabindex="-1"]');
    expect(document.activeElement).toBe(titulo);
    expect(titulo?.textContent).toContain('Ana');
    const texto = contenedor.textContent ?? '';
    for (const esperado of [
      'Consulta (30 min)',
      'jueves 10 de diciembre de 2026',
      '10:00',
      'Pendiente de confirmación',
      'Consultorio de ejemplo - Tel. +54 9 11 0000-0000',
      'Revisá tu email (paciente@ejemplo.com)',
      'los links para gestionarlo',
    ]) {
      expect(texto).toContain(esperado);
    }
    expect(texto).not.toContain('Si necesitás cambiarlo');
    expect(texto).not.toContain('99.999.999');
    expect(texto).not.toMatch(/token/i);
    expect(contenedor.querySelector('form')).toBeNull();
    expect(contenedor.querySelectorAll('h1')).toHaveLength(1);
    expect(contenedor.querySelector('a')).toBeNull();
  });

  it('un turno que nace CONFIRMADO lo dice e indica comunicarse con el consultorio', async () => {
    stubServidor(rutasBase({ [POST_TURNOS]: () => respuesta(201, { ...RESUMEN, estado: 'CONFIRMADO', puedeCancelar: false }) }));
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(contenedor.querySelector('h2[tabindex="-1"]')).not.toBeNull());
    const texto = contenedor.textContent ?? '';
    expect(texto).toContain('Confirmado');
    expect(texto).not.toContain('Pendiente de confirmación');
    expect(texto).toContain('Si necesitás cambiarlo, comunicate con el consultorio.');
    expect(texto).toContain('Revisá tu email (paciente@ejemplo.com): te enviamos los datos del turno.');
    expect(texto).not.toContain('links');
  });

  it('Reservar otro turno vuelve al paso de prestacion con el formulario vacio y la casilla desmarcada', async () => {
    const { llamadas } = stubServidor(rutasBase({ [POST_TURNOS]: () => respuesta(201, RESUMEN) }));
    await llegarAlFormulario();
    completarDatos();
    enviar();
    await vi.waitFor(() => expect(contenedor.querySelector('h2[tabindex="-1"]')).not.toBeNull());
    boton(contenedor, 'Reservar otro turno').click();
    await vi.waitFor(() => expect(porLabel('Consulta (30 min)').checked).toBe(false));
    expect(cantidad(llamadas, 'GET', '/api/public/prestaciones')).toBe(2);
    expect(oculto(campoPorLabel(contenedor, 'Fecha'))).toBe(true);
    elegirPrestacion('Consulta (30 min)');
    escribirFecha('2026-12-10');
    await vi.waitFor(() => expect(porLabel('10:00')).toBeTruthy());
    elegirHorario('10:00');
    expect(campoPorLabel(contenedor, 'Nombre').value).toBe('');
    expect(campoPorLabel(contenedor, 'Email').value).toBe('');
    expect(casilla().checked).toBe(false);
  });
});

describe('reservar: regiones y grupos accesibles', () => {
  it('tiene una region de estado y una de alerta, y grupos con leyenda', async () => {
    stubServidor(rutasBase());
    await llegarAlFormulario();
    expect(contenedor.querySelectorAll('[role="status"]').length).toBeGreaterThanOrEqual(2);
    expect(contenedor.querySelectorAll('.cfg-avisos [role="alert"]')).toHaveLength(1);
    const grupos = Array.from(contenedor.querySelectorAll('fieldset'));
    expect(grupos.length).toBeGreaterThanOrEqual(2);
    for (const grupo of grupos) {
      expect(grupo.querySelector(':scope > legend')?.textContent ?? '').not.toBe('');
    }
  });

  it('cada seccion tiene un encabezado h2 y los radios son nativos (navegables con flechas)', async () => {
    stubServidor(rutasBase());
    await llegarAlFormulario();
    expect(Array.from(contenedor.querySelectorAll('section h2')).map((h) => h.textContent)).toEqual([
      'Prestación',
      'Fecha y horario',
      'Tus datos',
    ]);
    const radios = Array.from(contenedor.querySelectorAll<HTMLInputElement>('input[name="prestacion"]'));
    expect(radios).toHaveLength(2);
  });
});
