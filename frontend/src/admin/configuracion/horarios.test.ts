import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ejemplo } from './ejemplos.testutil';
import { iniciar } from './horarios';
import { boton, campoPorLabel, respuesta, stubServidor } from './servidor.testutil';

const LISTA = ejemplo('horarios-listar.200');

let seccion: HTMLElement;

function enviar(): void {
  seccion.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

function completar(dia: string, inicio: string, fin: string): void {
  campoPorLabel(seccion, 'Día').value = dia;
  campoPorLabel(seccion, 'Hora de inicio').value = inicio;
  campoPorLabel(seccion, 'Hora de fin').value = fin;
}

function alertaForm(): string {
  return seccion.querySelector('form [role="alert"]')?.textContent ?? '';
}

beforeEach(() => {
  seccion = document.createElement('section');
  document.body.replaceChildren(seccion);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('seccion horarios: listado', () => {
  it('agrupa por dia (lunes a domingo) con varios rangos por dia y marca los dias sin horario', async () => {
    stubServidor({ 'GET /api/horarios-atencion': () => respuesta(200, LISTA) });
    await iniciar(seccion);
    expect(seccion.querySelector('h2')?.textContent).toBe('Horarios de atención');
    const dias = seccion.querySelectorAll('.cfg-dia');
    expect(dias).toHaveLength(7);
    expect(dias[0]?.querySelector('h3')?.textContent).toBe('Lunes');
    const lunes = Array.from(dias[0]?.querySelectorAll('.cfg-rango') ?? []).map((l) => l.textContent);
    expect(lunes).toHaveLength(2);
    expect(lunes[0]).toContain('09:00 a 13:00');
    expect(lunes[1]).toContain('15:00 a 19:00');
    expect(dias[1]?.querySelectorAll('.cfg-rango')).toHaveLength(1);
    expect(dias[2]?.textContent).toContain('Sin horario de atención');
  });

  it('ante error de carga muestra alerta con reintento', async () => {
    stubServidor({
      'GET /api/horarios-atencion': [() => respuesta(401, ejemplo('error.401-no-autenticado')), () => respuesta(200, LISTA)],
    });
    await iniciar(seccion);
    expect(seccion.querySelector('[role="alert"]')?.textContent).toMatch(/sesión/i);
    boton(seccion, 'Reintentar').click();
    await vi.waitFor(() => expect(seccion.querySelectorAll('.cfg-rango')).toHaveLength(3));
  });
});

describe('seccion horarios: alta y validacion', () => {
  it('crea un rango valido y recarga', async () => {
    const { llamadas } = stubServidor({
      'GET /api/horarios-atencion': () => respuesta(200, LISTA),
      'POST /api/horarios-atencion': () => respuesta(201, ejemplo('horarios-crear.201')),
    });
    await iniciar(seccion);
    completar('1', '09:00', '13:00');
    enviar();
    await vi.waitFor(() => expect(llamadas.filter((l) => l.metodo === 'GET')).toHaveLength(2));
    expect(llamadas.find((l) => l.metodo === 'POST')?.body).toEqual(ejemplo('horarios-crear.request'));
    expect(seccion.querySelector('[role="status"]')?.textContent).toMatch(/agregado/i);
  });

  it.each([
    ['13:00', '13:00'],
    ['14:00', '09:00'],
    ['', '10:00'],
    ['09:00', ''],
  ])('no envia con inicio %j y fin %j', async (inicio, fin) => {
    const { llamadas } = stubServidor({ 'GET /api/horarios-atencion': () => respuesta(200, LISTA) });
    await iniciar(seccion);
    completar('2', inicio, fin);
    enviar();
    expect(llamadas.filter((l) => l.metodo !== 'GET')).toHaveLength(0);
    const invalido = seccion.querySelectorAll('form [aria-invalid="true"]');
    expect(invalido.length).toBeGreaterThan(0);
    const id = invalido[0]?.getAttribute('aria-describedby') ?? '';
    expect(document.getElementById(id)?.textContent).not.toBe('');
  });

  it('422 HORARIOS_SOLAPADOS se muestra junto al formulario y conserva los valores', async () => {
    stubServidor({
      'GET /api/horarios-atencion': () => respuesta(200, LISTA),
      'POST /api/horarios-atencion': () => respuesta(422, ejemplo('horarios-crear.422-horarios-solapados')),
    });
    await iniciar(seccion);
    completar('1', '10:00', '12:00');
    enviar();
    await vi.waitFor(() => expect(alertaForm()).toMatch(/superpone/i));
    expect(campoPorLabel(seccion, 'Hora de inicio').value).toBe('10:00');
    expect(campoPorLabel(seccion, 'Hora de fin').value).toBe('12:00');
    expect(campoPorLabel(seccion, 'Día').value).toBe('1');
  });

  it('422 RANGO_HORARIO_INVALIDO del servidor se muestra comprensible', async () => {
    stubServidor({
      'GET /api/horarios-atencion': () => respuesta(200, LISTA),
      'POST /api/horarios-atencion': () => respuesta(422, ejemplo('horarios-crear.422-rango-horario-invalido')),
    });
    await iniciar(seccion);
    completar('1', '10:00', '12:00');
    enviar();
    await vi.waitFor(() => expect(alertaForm()).toMatch(/anterior/i));
  });

  it('un 400 VALIDACION con detalles de horaFin se muestra junto al campo', async () => {
    stubServidor({
      'GET /api/horarios-atencion': () => respuesta(200, LISTA),
      'POST /api/horarios-atencion': () =>
        respuesta(400, {
          timestamp: '2026-11-30T10:15:00-03:00',
          status: 400,
          error: 'Bad Request',
          codigo: 'VALIDACION',
          mensaje: 'Hay campos con errores.',
          path: '/api/horarios-atencion',
          detalles: [{ campo: 'horaFin', mensaje: 'Formato de hora inválido.' }],
        }),
    });
    await iniciar(seccion);
    completar('1', '10:00', '12:00');
    enviar();
    await vi.waitFor(() => expect(campoPorLabel(seccion, 'Hora de fin').getAttribute('aria-invalid')).toBe('true'));
    const id = campoPorLabel(seccion, 'Hora de fin').getAttribute('aria-describedby') ?? '';
    expect(document.getElementById(id)?.textContent).toBe('Formato de hora inválido.');
  });
});

describe('seccion horarios: edicion y baja', () => {
  it('edita un rango con PUT y vuelve al alta', async () => {
    const { llamadas } = stubServidor({
      'GET /api/horarios-atencion': () => respuesta(200, LISTA),
      'PUT /api/horarios-atencion/1': () => respuesta(200, ejemplo('horarios-actualizar.200')),
    });
    await iniciar(seccion);
    boton(seccion, 'Editar Lunes 09:00 a 13:00').click();
    expect(campoPorLabel(seccion, 'Hora de fin').value).toBe('13:00');
    expect(document.activeElement).toBe(campoPorLabel(seccion, 'Día'));
    campoPorLabel(seccion, 'Hora de fin').value = '14:00';
    enviar();
    await vi.waitFor(() => expect(llamadas.some((l) => l.metodo === 'PUT')).toBe(true));
    expect(llamadas.find((l) => l.metodo === 'PUT')?.body).toEqual(ejemplo('horarios-actualizar.request'));
    await vi.waitFor(() => expect(seccion.querySelector('legend')?.textContent).toBe('Nuevo rango'));
  });

  it('PUT con 422 HORARIOS_SOLAPADOS mantiene la edicion', async () => {
    stubServidor({
      'GET /api/horarios-atencion': () => respuesta(200, LISTA),
      'PUT /api/horarios-atencion/1': () => respuesta(422, ejemplo('horarios-actualizar.422-horarios-solapados')),
    });
    await iniciar(seccion);
    boton(seccion, 'Editar Lunes 09:00 a 13:00').click();
    campoPorLabel(seccion, 'Hora de fin').value = '16:00';
    enviar();
    await vi.waitFor(() => expect(alertaForm()).toMatch(/superpone/i));
    expect(seccion.querySelector('legend')?.textContent).toBe('Editar rango');
    expect(campoPorLabel(seccion, 'Hora de fin').value).toBe('16:00');
  });

  it('cancelar edicion limpia el formulario', async () => {
    stubServidor({ 'GET /api/horarios-atencion': () => respuesta(200, LISTA) });
    await iniciar(seccion);
    boton(seccion, 'Editar Lunes 09:00 a 13:00').click();
    boton(seccion, 'Cancelar edición').click();
    expect(campoPorLabel(seccion, 'Hora de inicio').value).toBe('');
    expect(seccion.querySelector('legend')?.textContent).toBe('Nuevo rango');
  });

  it('elimina con confirmacion', async () => {
    const { llamadas } = stubServidor({
      'GET /api/horarios-atencion': [() => respuesta(200, LISTA), () => respuesta(200, [])],
      'DELETE /api/horarios-atencion/3': () => respuesta(204),
    });
    await iniciar(seccion);
    boton(seccion, 'Eliminar Martes 09:00 a 13:00').click();
    expect(llamadas.some((l) => l.metodo === 'DELETE')).toBe(false);
    boton(seccion, 'Confirmar eliminar Martes 09:00 a 13:00').click();
    await vi.waitFor(() => expect(seccion.querySelectorAll('.cfg-rango')).toHaveLength(0));
    expect(llamadas.some((l) => l.metodo === 'DELETE' && l.url === '/api/horarios-atencion/3')).toBe(true);
  });

  it('cancelar la confirmacion no elimina', async () => {
    const { llamadas } = stubServidor({ 'GET /api/horarios-atencion': () => respuesta(200, LISTA) });
    await iniciar(seccion);
    boton(seccion, 'Eliminar Martes 09:00 a 13:00').click();
    boton(seccion, 'Cancelar eliminar Martes 09:00 a 13:00').click();
    expect(llamadas.some((l) => l.metodo === 'DELETE')).toBe(false);
  });
});

describe('seccion horarios: accesibilidad', () => {
  it('todos los controles tienen label, hay fieldset/legend y regiones de aviso', async () => {
    stubServidor({ 'GET /api/horarios-atencion': () => respuesta(200, LISTA) });
    await iniciar(seccion);
    const controles = Array.from(seccion.querySelectorAll('form input, form select'));
    expect(controles).toHaveLength(3);
    for (const c of controles) {
      expect((c as HTMLInputElement).labels?.length).toBeGreaterThan(0);
    }
    expect(seccion.querySelector('fieldset legend')).not.toBeNull();
    expect(seccion.querySelectorAll('[role="alert"]').length).toBeGreaterThanOrEqual(1);
    expect(campoPorLabel(seccion, 'Día').querySelectorAll('option')).toHaveLength(7);
  });
});
