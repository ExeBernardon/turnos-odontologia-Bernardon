import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ejemplo } from './ejemplos.testutil';
import { iniciar } from './prestaciones';
import { boton, campoPorLabel, respuesta, stubServidor } from './servidor.testutil';

const LISTA = ejemplo('prestaciones-listar.200');

let seccion: HTMLElement;

async function cargar(): Promise<void> {
  await iniciar(seccion);
}

function enviar(): void {
  const form = seccion.querySelector('form');
  form?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

function escribir(label: string, valor: string): void {
  campoPorLabel(seccion, label).value = valor;
}

beforeEach(() => {
  seccion = document.createElement('section');
  document.body.replaceChildren(seccion);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('seccion prestaciones: listado', () => {
  it('muestra un h2 y lista activas e inactivas con su estado en texto', async () => {
    stubServidor({ 'GET /api/prestaciones': () => respuesta(200, LISTA) });
    await cargar();
    expect(seccion.querySelector('h2')?.textContent).toBe('Prestaciones');
    const items = seccion.querySelectorAll('li');
    expect(items).toHaveLength(3);
    expect(items[0]?.textContent).toContain('Consulta');
    expect(items[0]?.textContent).toContain('30 min');
    expect(items[0]?.textContent).toContain('Activa');
    expect(items[2]?.textContent).toContain('Inactiva');
  });

  it('muestra un mensaje si no hay prestaciones', async () => {
    stubServidor({ 'GET /api/prestaciones': () => respuesta(200, []) });
    await cargar();
    expect(seccion.textContent).toContain('No hay prestaciones cargadas');
    expect(seccion.querySelectorAll('li')).toHaveLength(0);
  });

  it('ante error de carga muestra alerta y permite reintentar', async () => {
    const { llamadas } = stubServidor({
      'GET /api/prestaciones': [() => respuesta(500, ejemplo('error.500-error-interno')), () => respuesta(200, LISTA)],
    });
    await cargar();
    expect(seccion.querySelector('[role="alert"]')?.textContent).toMatch(/no se pudo/i);
    boton(seccion, 'Reintentar').click();
    await vi.waitFor(() => expect(seccion.querySelectorAll('li')).toHaveLength(3));
    expect(llamadas).toHaveLength(2);
    expect(seccion.querySelector('[role="alert"]')?.textContent).toBe('');
  });
});

describe('seccion prestaciones: alta y validacion', () => {
  it('crea una prestacion valida (activa por defecto) y recarga el listado', async () => {
    const { llamadas } = stubServidor({
      'GET /api/prestaciones': () => respuesta(200, LISTA),
      'POST /api/prestaciones': () => respuesta(201, ejemplo('prestaciones-crear.201')),
    });
    await cargar();
    escribir('Nombre', '  Consulta ');
    escribir('Duración (minutos)', '30');
    enviar();
    await vi.waitFor(() => expect(llamadas.filter((l) => l.metodo === 'GET')).toHaveLength(2));
    expect(llamadas.find((l) => l.metodo === 'POST')?.body).toEqual({ nombre: 'Consulta', duracionMinutos: 30, activa: true });
    expect(seccion.querySelector('[role="status"]')?.textContent).toMatch(/creada/i);
    expect(campoPorLabel(seccion, 'Nombre').value).toBe('');
  });

  it.each(['0', '-3', '2.5', '500', ''])('no envia con duracion %j y marca el campo', async (duracion) => {
    const { llamadas } = stubServidor({ 'GET /api/prestaciones': () => respuesta(200, LISTA) });
    await cargar();
    escribir('Nombre', 'Limpieza');
    escribir('Duración (minutos)', duracion);
    enviar();
    expect(llamadas.filter((l) => l.metodo !== 'GET')).toHaveLength(0);
    const campo = campoPorLabel(seccion, 'Duración (minutos)');
    expect(campo.getAttribute('aria-invalid')).toBe('true');
    const idError = campo.getAttribute('aria-describedby') ?? '';
    expect(document.getElementById(idError)?.textContent).toMatch(/duración/i);
  });

  it('no envia con nombre vacio y enfoca el primer campo invalido', async () => {
    const { llamadas } = stubServidor({ 'GET /api/prestaciones': () => respuesta(200, LISTA) });
    await cargar();
    escribir('Nombre', '   ');
    escribir('Duración (minutos)', '30');
    enviar();
    expect(llamadas.filter((l) => l.metodo !== 'GET')).toHaveLength(0);
    const nombre = campoPorLabel(seccion, 'Nombre');
    expect(nombre.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(nombre);
  });

  it('un 400 VALIDACION del servidor se muestra junto al campo', async () => {
    stubServidor({
      'GET /api/prestaciones': () => respuesta(200, LISTA),
      'POST /api/prestaciones': () =>
        respuesta(400, {
          timestamp: '2026-11-30T10:15:00-03:00',
          status: 400,
          error: 'Bad Request',
          codigo: 'VALIDACION',
          mensaje: 'Hay campos con errores.',
          path: '/api/prestaciones',
          detalles: [{ campo: 'nombre', mensaje: 'El nombre ya existe.' }],
        }),
    });
    await cargar();
    escribir('Nombre', 'Consulta');
    escribir('Duración (minutos)', '30');
    enviar();
    await vi.waitFor(() => expect(campoPorLabel(seccion, 'Nombre').getAttribute('aria-invalid')).toBe('true'));
    const id = campoPorLabel(seccion, 'Nombre').getAttribute('aria-describedby') ?? '';
    expect(document.getElementById(id)?.textContent).toBe('El nombre ya existe.');
  });

  it('sin conexion informa y conserva lo ingresado', async () => {
    stubServidor({
      'GET /api/prestaciones': () => respuesta(200, LISTA),
      'POST /api/prestaciones': () => {
        throw new TypeError('network');
      },
    });
    await cargar();
    escribir('Nombre', 'Limpieza');
    escribir('Duración (minutos)', '45');
    enviar();
    await vi.waitFor(() => expect(seccion.querySelector('[role="alert"]')?.textContent).toMatch(/conectarnos/i));
    expect(campoPorLabel(seccion, 'Nombre').value).toBe('Limpieza');
    expect(campoPorLabel(seccion, 'Duración (minutos)').value).toBe('45');
  });
});

describe('seccion prestaciones: edicion y estado', () => {
  it('editar carga el formulario y guarda con PUT conservando activa', async () => {
    const { llamadas } = stubServidor({
      'GET /api/prestaciones': () => respuesta(200, LISTA),
      'PUT /api/prestaciones/3': () => respuesta(200, ejemplo('prestaciones-actualizar.200')),
    });
    await cargar();
    boton(seccion, 'Editar Control de ortodoncia').click();
    expect(campoPorLabel(seccion, 'Nombre').value).toBe('Control de ortodoncia');
    expect(campoPorLabel(seccion, 'Duración (minutos)').value).toBe('60');
    expect(document.activeElement).toBe(campoPorLabel(seccion, 'Nombre'));
    escribir('Duración (minutos)', '50');
    enviar();
    await vi.waitFor(() => expect(llamadas.some((l) => l.metodo === 'PUT')).toBe(true));
    expect(llamadas.find((l) => l.metodo === 'PUT')?.body).toEqual({
      nombre: 'Control de ortodoncia',
      duracionMinutos: 50,
      activa: false,
    });
  });

  it('cancelar edicion vuelve al formulario de alta', async () => {
    stubServidor({ 'GET /api/prestaciones': () => respuesta(200, LISTA) });
    await cargar();
    boton(seccion, 'Editar Consulta').click();
    boton(seccion, 'Cancelar edición').click();
    expect(campoPorLabel(seccion, 'Nombre').value).toBe('');
    expect(seccion.querySelector('legend')?.textContent).toBe('Nueva prestación');
  });

  it('desactivar y activar envian PUT con activa invertida', async () => {
    const { llamadas } = stubServidor({
      'GET /api/prestaciones': () => respuesta(200, LISTA),
      'PUT /api/prestaciones/1': () => respuesta(200, ejemplo('prestaciones-actualizar.200')),
      'PUT /api/prestaciones/3': () => respuesta(200, ejemplo('prestaciones-actualizar.200')),
    });
    await cargar();
    boton(seccion, 'Desactivar Consulta').click();
    await vi.waitFor(() => expect(llamadas.filter((l) => l.metodo === 'PUT')).toHaveLength(1));
    expect(llamadas.find((l) => l.url === '/api/prestaciones/1')?.body).toEqual({ nombre: 'Consulta', duracionMinutos: 30, activa: false });
    await vi.waitFor(() => expect(llamadas.filter((l) => l.metodo === 'GET')).toHaveLength(2));
    boton(seccion, 'Activar Control de ortodoncia').click();
    await vi.waitFor(() => expect(llamadas.filter((l) => l.metodo === 'PUT')).toHaveLength(2));
    expect(llamadas.find((l) => l.url === '/api/prestaciones/3')?.body).toMatchObject({ activa: true });
  });
});

describe('seccion prestaciones: baja', () => {
  it('pide confirmacion y elimina', async () => {
    const { llamadas } = stubServidor({
      'GET /api/prestaciones': [() => respuesta(200, LISTA), () => respuesta(200, [])],
      'DELETE /api/prestaciones/2': () => respuesta(204),
    });
    await cargar();
    boton(seccion, 'Eliminar Limpieza').click();
    expect(llamadas.some((l) => l.metodo === 'DELETE')).toBe(false);
    expect(seccion.textContent).toContain('¿Eliminar');
    boton(seccion, 'Confirmar eliminar Limpieza').click();
    await vi.waitFor(() => expect(seccion.textContent).toContain('No hay prestaciones cargadas'));
    expect(llamadas.some((l) => l.metodo === 'DELETE' && l.url === '/api/prestaciones/2')).toBe(true);
  });

  it('cancelar la confirmacion no elimina', async () => {
    const { llamadas } = stubServidor({ 'GET /api/prestaciones': () => respuesta(200, LISTA) });
    await cargar();
    boton(seccion, 'Eliminar Limpieza').click();
    boton(seccion, 'Cancelar eliminar Limpieza').click();
    expect(llamadas.some((l) => l.metodo === 'DELETE')).toBe(false);
    expect(boton(seccion, 'Eliminar Limpieza')).toBeDefined();
  });

  it('422 PRESTACION_CON_TURNOS explica y ofrece desactivar en su lugar', async () => {
    const { llamadas } = stubServidor({
      'GET /api/prestaciones': () => respuesta(200, LISTA),
      'DELETE /api/prestaciones/1': () => respuesta(422, ejemplo('prestaciones-eliminar.422-prestacion-con-turnos')),
      'PUT /api/prestaciones/1': () => respuesta(200, ejemplo('prestaciones-actualizar.200')),
    });
    await cargar();
    boton(seccion, 'Eliminar Consulta').click();
    boton(seccion, 'Confirmar eliminar Consulta').click();
    await vi.waitFor(() => expect(seccion.querySelector('[role="alert"]')?.textContent).toMatch(/turnos/i));
    expect(seccion.querySelector('[role="alert"]')?.textContent).toMatch(/desactiv/i);
    expect(seccion.querySelectorAll('li')).toHaveLength(3);
    boton(seccion, 'Desactivar en su lugar').click();
    await vi.waitFor(() => expect(llamadas.some((l) => l.metodo === 'PUT')).toBe(true));
    expect(llamadas.find((l) => l.metodo === 'PUT')?.body).toMatchObject({ activa: false });
  });
});

describe('seccion prestaciones: accesibilidad', () => {
  it('todos los controles del formulario tienen label asociado y hay regiones de estado', async () => {
    stubServidor({ 'GET /api/prestaciones': () => respuesta(200, LISTA) });
    await cargar();
    const controles = seccion.querySelectorAll('form input, form select');
    expect(controles.length).toBeGreaterThanOrEqual(2);
    for (const c of Array.from(controles)) {
      expect((c as HTMLInputElement).labels?.length).toBeGreaterThan(0);
    }
    expect(seccion.querySelector('[role="status"]')).not.toBeNull();
    expect(seccion.querySelector('[role="alert"]')).not.toBeNull();
    expect(seccion.querySelector('fieldset legend')).not.toBeNull();
  });
});
