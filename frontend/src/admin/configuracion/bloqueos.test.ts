import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { iniciar } from './bloqueos';
import { ejemplo } from './ejemplos.testutil';
import { boton, campoPorLabel, respuesta, stubServidor } from './servidor.testutil';

const LISTA = ejemplo('bloqueos-listar.200');
const PREVIEW = ejemplo('bloqueos-previsualizar.200');
const CREADO = ejemplo('bloqueos-crear.201');
const CONFLICTO = ejemplo('bloqueos-crear.409-bloqueo-con-turnos-afectados');
const RANGO422 = ejemplo('bloqueos-crear.422-rango-horario-invalido');
const VALIDACION400 = ejemplo('error.400-validacion');

const GET_DEFECTO = 'GET /api/bloqueos?desde=2026-11-24&hasta=2026-12-24';

let seccion: HTMLElement;

function enviar(id: string): void {
  seccion.querySelector('#' + id)?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

function escribir(label: string, valor: string): void {
  campoPorLabel(seccion, label).value = valor;
}

function completarAlta(motivo = 'Capacitación'): void {
  escribir('Inicio', '2026-12-24T09:00');
  escribir('Fin', '2026-12-24T13:00');
  escribir('Motivo (opcional)', motivo);
}

function botonQueEmpiezaCon(prefijo: string): HTMLButtonElement {
  const b = Array.from(seccion.querySelectorAll('button')).find((x) => (x.getAttribute('aria-label') ?? '').startsWith(prefijo));
  if (b === undefined) {
    throw new Error('boton no encontrado: ' + prefijo);
  }
  return b;
}

const alerta = (): string => seccion.querySelector('[role="alert"]')?.textContent ?? '';
const estado = (): string => seccion.querySelector('[role="status"]')?.textContent ?? '';

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-24T15:00:00Z'));
  seccion = document.createElement('section');
  document.body.replaceChildren(seccion);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('seccion bloqueos: listado', () => {
  it('consulta hoy..+30 dias y lista los bloqueos con fechas en hora de Buenos Aires', async () => {
    const { llamadas } = stubServidor({ [GET_DEFECTO]: () => respuesta(200, LISTA) });
    await iniciar(seccion);
    expect(llamadas[0]?.url).toBe('/api/bloqueos?desde=2026-11-24&hasta=2026-12-24');
    expect(seccion.querySelector('h2')?.textContent).toBe('Bloqueos');
    const items = seccion.querySelectorAll('li.cfg-item');
    expect(items).toHaveLength(1);
    expect(items[0]?.textContent).toContain('24/12/2026');
    expect(items[0]?.textContent).toContain('09:00');
    expect(items[0]?.textContent).toContain('13:00');
    expect(items[0]?.textContent).toContain('Capacitación');
    expect(campoPorLabel(seccion, 'Desde').value).toBe('2026-11-24');
    expect(campoPorLabel(seccion, 'Hasta').value).toBe('2026-12-24');
  });

  it('muestra "Sin motivo" cuando el motivo es null', async () => {
    stubServidor({ [GET_DEFECTO]: () => respuesta(200, [{ id: 2, inicio: '2026-12-01T12:00:00Z', fin: '2026-12-01T15:00:00Z', motivo: null, creadoEn: '2026-11-23T09:00:00-03:00' }]) });
    await iniciar(seccion);
    expect(seccion.querySelector('li.cfg-item')?.textContent).toContain('Sin motivo');
    expect(seccion.querySelector('li.cfg-item')?.textContent).toContain('09:00');
  });

  it('muestra un mensaje si no hay bloqueos en el rango', async () => {
    stubServidor({ [GET_DEFECTO]: () => respuesta(200, []) });
    await iniciar(seccion);
    expect(seccion.textContent).toContain('No hay bloqueos en el rango');
    expect(seccion.querySelectorAll('li.cfg-item')).toHaveLength(0);
  });

  it('ante error de carga muestra alerta y permite reintentar', async () => {
    const { llamadas } = stubServidor({
      [GET_DEFECTO]: [() => respuesta(500, ejemplo('error.500-error-interno')), () => respuesta(200, LISTA)],
    });
    await iniciar(seccion);
    expect(alerta()).toMatch(/no se pudo/i);
    boton(seccion, 'Reintentar').click();
    await vi.waitFor(() => expect(seccion.querySelectorAll('li.cfg-item')).toHaveLength(1));
    expect(llamadas).toHaveLength(2);
    expect(alerta()).toBe('');
  });

  it('cambiar el rango consulta con las nuevas fechas', async () => {
    const { llamadas } = stubServidor({
      [GET_DEFECTO]: () => respuesta(200, LISTA),
      'GET /api/bloqueos?desde=2026-12-01&hasta=2026-12-10': () => respuesta(200, []),
    });
    await iniciar(seccion);
    escribir('Desde', '2026-12-01');
    escribir('Hasta', '2026-12-10');
    enviar('bloq-filtro');
    await vi.waitFor(() => expect(seccion.textContent).toContain('No hay bloqueos en el rango'));
    expect(llamadas[1]?.url).toBe('/api/bloqueos?desde=2026-12-01&hasta=2026-12-10');
  });

  it('un rango de mas de 31 dias no consulta y marca el campo hasta', async () => {
    const { llamadas } = stubServidor({ [GET_DEFECTO]: () => respuesta(200, LISTA) });
    await iniciar(seccion);
    escribir('Hasta', '2026-12-25');
    enviar('bloq-filtro');
    const hasta = campoPorLabel(seccion, 'Hasta');
    expect(hasta.getAttribute('aria-invalid')).toBe('true');
    expect(seccion.querySelector('#' + hasta.id + '-error')?.textContent).toMatch(/31/);
    expect(llamadas).toHaveLength(1);
  });
});

describe('seccion bloqueos: alta en dos pasos', () => {
  it('sin turnos afectados previsualiza y crea directo, enviando offset -03:00', async () => {
    const { llamadas } = stubServidor({
      [GET_DEFECTO]: [() => respuesta(200, []), () => respuesta(200, LISTA)],
      'POST /api/bloqueos/previsualizar': () => respuesta(200, { turnosAfectados: [] }),
      'POST /api/bloqueos': () => respuesta(201, { bloqueo: (CREADO as { bloqueo: unknown }).bloqueo, turnosCancelados: [] }),
    });
    await iniciar(seccion);
    completarAlta();
    enviar('bloq-alta');
    await vi.waitFor(() => expect(seccion.querySelectorAll('li.cfg-item')).toHaveLength(1));
    const previa = llamadas.find((l) => l.url === '/api/bloqueos/previsualizar');
    expect(previa?.body).toEqual({ inicio: '2026-12-24T09:00:00-03:00', fin: '2026-12-24T13:00:00-03:00', motivo: 'Capacitación' });
    const crear = llamadas.find((l) => l.metodo === 'POST' && l.url === '/api/bloqueos');
    expect(crear?.body).toEqual({
      inicio: '2026-12-24T09:00:00-03:00',
      fin: '2026-12-24T13:00:00-03:00',
      motivo: 'Capacitación',
      cancelarTurnosAfectados: false,
    });
    expect(estado()).toBe('Bloqueo creado.');
    expect(campoPorLabel(seccion, 'Inicio').value).toBe('');
    expect(seccion.querySelector('[data-confirmacion]')).toBeNull();
  });

  it('con turnos afectados muestra la lista con aviso y no crea hasta confirmar', async () => {
    const { llamadas } = stubServidor({
      [GET_DEFECTO]: () => respuesta(200, []),
      'POST /api/bloqueos/previsualizar': () => respuesta(200, PREVIEW),
    });
    await iniciar(seccion);
    completarAlta();
    enviar('bloq-alta');
    await vi.waitFor(() => expect(seccion.querySelector('[data-confirmacion]')).not.toBeNull());
    const panel = seccion.querySelector('[data-confirmacion]');
    expect(panel?.textContent).toMatch(/se cancelar[aá]n 2 turnos/i);
    expect(panel?.querySelectorAll('li')).toHaveLength(2);
    expect(panel?.textContent).toContain('Ana Ejemplo');
    expect(panel?.textContent).toContain('Consulta');
    expect(panel?.textContent).toContain('10:00');
    expect(llamadas.some((l) => l.metodo === 'POST' && l.url === '/api/bloqueos')).toBe(false);
  });

  it('confirmar crea con cancelarTurnosAfectados=true e informa los turnos cancelados', async () => {
    const { llamadas } = stubServidor({
      [GET_DEFECTO]: [() => respuesta(200, []), () => respuesta(200, LISTA)],
      'POST /api/bloqueos/previsualizar': () => respuesta(200, PREVIEW),
      'POST /api/bloqueos': () => respuesta(201, CREADO),
    });
    await iniciar(seccion);
    completarAlta();
    enviar('bloq-alta');
    await vi.waitFor(() => expect(seccion.querySelector('[data-confirmacion]')).not.toBeNull());
    boton(seccion, 'Confirmar y cancelar turnos').click();
    await vi.waitFor(() => expect(seccion.querySelectorAll('li.cfg-item')).toHaveLength(1));
    const crear = llamadas.find((l) => l.metodo === 'POST' && l.url === '/api/bloqueos');
    expect(crear?.body).toMatchObject({ cancelarTurnosAfectados: true, motivo: 'Capacitación' });
    expect(estado()).toBe('Bloqueo creado. Se cancelaron 2 turnos.');
    expect(seccion.querySelector('[data-confirmacion]')).toBeNull();
  });

  it('volver cancela el flujo: no crea nada y conserva los valores', async () => {
    const { llamadas } = stubServidor({
      [GET_DEFECTO]: () => respuesta(200, []),
      'POST /api/bloqueos/previsualizar': () => respuesta(200, PREVIEW),
    });
    await iniciar(seccion);
    completarAlta('Congreso');
    enviar('bloq-alta');
    await vi.waitFor(() => expect(seccion.querySelector('[data-confirmacion]')).not.toBeNull());
    boton(seccion, 'Volver').click();
    expect(seccion.querySelector('[data-confirmacion]')).toBeNull();
    expect(campoPorLabel(seccion, 'Inicio').value).toBe('2026-12-24T09:00');
    expect(campoPorLabel(seccion, 'Motivo (opcional)').value).toBe('Congreso');
    expect(llamadas.some((l) => l.metodo === 'POST' && l.url === '/api/bloqueos')).toBe(false);
  });

  it('409 al crear (turno nuevo tras la vista previa) muestra la lista nueva y pide confirmar de nuevo', async () => {
    const { llamadas } = stubServidor({
      [GET_DEFECTO]: () => respuesta(200, []),
      'POST /api/bloqueos/previsualizar': () => respuesta(200, { turnosAfectados: [] }),
      'POST /api/bloqueos': [() => respuesta(409, CONFLICTO), () => respuesta(201, CREADO)],
    });
    await iniciar(seccion);
    completarAlta();
    enviar('bloq-alta');
    await vi.waitFor(() => expect(seccion.querySelector('[data-confirmacion]')).not.toBeNull());
    expect(seccion.querySelector('[data-confirmacion]')?.querySelectorAll('li')).toHaveLength(2);
    expect(seccion.querySelector('[data-confirmacion]')?.textContent).toContain('Bruno Prueba');
    expect(llamadas.filter((l) => l.metodo === 'POST' && l.url === '/api/bloqueos')).toHaveLength(1);
    boton(seccion, 'Confirmar y cancelar turnos').click();
    await vi.waitFor(() => expect(estado()).toMatch(/Bloqueo creado/));
    const creaciones = llamadas.filter((l) => l.metodo === 'POST' && l.url === '/api/bloqueos');
    expect(creaciones[1]?.body).toMatchObject({ cancelarTurnosAfectados: true });
  });

  it('no envia nada si el formulario es invalido y marca los campos', async () => {
    const { llamadas } = stubServidor({ [GET_DEFECTO]: () => respuesta(200, []) });
    await iniciar(seccion);
    enviar('bloq-alta');
    expect(campoPorLabel(seccion, 'Inicio').getAttribute('aria-invalid')).toBe('true');
    expect(campoPorLabel(seccion, 'Fin').getAttribute('aria-invalid')).toBe('true');
    escribir('Inicio', '2026-12-24T13:00');
    escribir('Fin', '2026-12-24T09:00');
    enviar('bloq-alta');
    expect(campoPorLabel(seccion, 'Inicio').hasAttribute('aria-invalid')).toBe(false);
    expect(seccion.querySelector('#' + campoPorLabel(seccion, 'Fin').id + '-error')?.textContent).toMatch(/anterior/);
    expect(llamadas).toHaveLength(1);
  });

  it('422 RANGO_HORARIO_INVALIDO se muestra y conserva los valores', async () => {
    stubServidor({
      [GET_DEFECTO]: () => respuesta(200, []),
      'POST /api/bloqueos/previsualizar': () => respuesta(422, RANGO422),
    });
    await iniciar(seccion);
    completarAlta();
    enviar('bloq-alta');
    await vi.waitFor(() => expect(alerta()).toMatch(/inicio debe ser anterior/i));
    expect(campoPorLabel(seccion, 'Motivo (opcional)').value).toBe('Capacitación');
    expect(seccion.querySelector('[data-confirmacion]')).toBeNull();
  });

  it('400 VALIDACION con detalles por campo marca el campo con aria-invalid', async () => {
    const cuerpo = { ...(VALIDACION400 as object), detalles: [{ campo: 'motivo', mensaje: 'El motivo es demasiado largo' }] };
    stubServidor({
      [GET_DEFECTO]: () => respuesta(200, []),
      'POST /api/bloqueos/previsualizar': () => respuesta(400, cuerpo),
    });
    await iniciar(seccion);
    completarAlta();
    enviar('bloq-alta');
    const motivo = campoPorLabel(seccion, 'Motivo (opcional)');
    await vi.waitFor(() => expect(motivo.getAttribute('aria-invalid')).toBe('true'));
    expect(seccion.querySelector('#' + motivo.id + '-error')?.textContent).toBe('El motivo es demasiado largo');
  });
});

describe('seccion bloqueos: baja', () => {
  it('confirmar la baja llama a DELETE y recarga el listado', async () => {
    const { llamadas } = stubServidor({
      [GET_DEFECTO]: [() => respuesta(200, LISTA), () => respuesta(200, [])],
      'DELETE /api/bloqueos/1': () => respuesta(204),
    });
    await iniciar(seccion);
    botonQueEmpiezaCon('Eliminar bloqueo').click();
    expect(seccion.textContent).toMatch(/no restituye/i);
    botonQueEmpiezaCon('Confirmar eliminar bloqueo').click();
    await vi.waitFor(() => expect(seccion.textContent).toContain('No hay bloqueos en el rango'));
    expect(llamadas.some((l) => l.metodo === 'DELETE' && l.url === '/api/bloqueos/1')).toBe(true);
    expect(estado()).toBe('Bloqueo eliminado.');
  });

  it('cancelar la baja no llama a la API y el bloqueo permanece', async () => {
    const { llamadas } = stubServidor({ [GET_DEFECTO]: () => respuesta(200, LISTA) });
    await iniciar(seccion);
    botonQueEmpiezaCon('Eliminar bloqueo').click();
    botonQueEmpiezaCon('Cancelar eliminar bloqueo').click();
    expect(seccion.querySelectorAll('li.cfg-item')).toHaveLength(1);
    expect(llamadas).toHaveLength(1);
    expect(botonQueEmpiezaCon('Eliminar bloqueo')).toBeDefined();
  });

  it('404 al eliminar informa que ya no existe y recarga', async () => {
    const { llamadas } = stubServidor({
      [GET_DEFECTO]: [() => respuesta(200, LISTA), () => respuesta(200, [])],
      'DELETE /api/bloqueos/1': () => respuesta(404, ejemplo('error.404-recurso-no-encontrado')),
    });
    await iniciar(seccion);
    botonQueEmpiezaCon('Eliminar bloqueo').click();
    botonQueEmpiezaCon('Confirmar eliminar bloqueo').click();
    await vi.waitFor(() => expect(seccion.textContent).toContain('No hay bloqueos en el rango'));
    expect(alerta()).toMatch(/ya no existe/i);
    expect(llamadas.filter((l) => l.metodo === 'GET')).toHaveLength(2);
  });
});

describe('seccion bloqueos: accesibilidad', () => {
  it('cada campo tiene label, hay regiones status y alert', async () => {
    stubServidor({ [GET_DEFECTO]: () => respuesta(200, []) });
    await iniciar(seccion);
    for (const label of ['Desde', 'Hasta', 'Inicio', 'Fin', 'Motivo (opcional)']) {
      const campo = campoPorLabel(seccion, label);
      expect(campo.getAttribute('aria-describedby')).toBe(campo.id + '-error');
    }
    expect(campoPorLabel(seccion, 'Inicio').type).toBe('datetime-local');
    expect(seccion.querySelector('[role="status"]')).not.toBeNull();
    expect(seccion.querySelector('[role="alert"]')).not.toBeNull();
  });
});
