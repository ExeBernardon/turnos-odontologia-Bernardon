import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ejemplo } from './configuracion/ejemplos.testutil';
import { boton, campoPorLabel, respuesta, stubServidor } from './configuracion/servidor.testutil';
import { iniciar } from './pacientes';

const LISTA = ejemplo('pacientes-listar.200');
const ANA = ejemplo('pacientes-obtener.200');
const CARLA = ejemplo('pacientes-crear.201');

function errorApi(status: number, codigo: string, mensaje: string, path: string, detalles?: unknown): unknown {
  return {
    timestamp: '2026-11-30T10:15:00-03:00',
    status,
    error: 'Error',
    codigo,
    mensaje,
    path,
    ...(detalles === undefined ? {} : { detalles }),
  };
}

let pantalla: HTMLElement;

async function asentar(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
}

async function cargar(): Promise<void> {
  await iniciar(pantalla);
}

async function clic(nombre: string): Promise<void> {
  boton(pantalla, nombre).click();
  await asentar();
}

function escribir(label: string, valor: string): void {
  campoPorLabel(pantalla, label).value = valor;
}

async function enviarFormulario(): Promise<void> {
  const form = pantalla.querySelector('form.pac-form');
  form?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  await asentar();
}

function detalle(): HTMLElement {
  const d = pantalla.querySelector<HTMLElement>('.pac-detalle');
  if (d === null) {
    throw new Error('sin detalle');
  }
  return d;
}

function alertaGeneral(): string {
  return pantalla.querySelector('.cfg-avisos [role="alert"]')?.textContent ?? '';
}

function estadoGeneral(): string {
  return pantalla.querySelector('.cfg-avisos [role="status"]')?.textContent ?? '';
}

function urls(llamadas: { metodo: string; url: string }[]): string[] {
  return llamadas.map((l) => l.metodo + ' ' + l.url);
}

beforeEach(() => {
  pantalla = document.createElement('main');
  document.body.replaceChildren(pantalla);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('pantalla pacientes: carga inicial', () => {
  it('tiene un unico h1 Pacientes y carga la lista completa sin q', async () => {
    const { llamadas } = stubServidor({ 'GET /api/pacientes': () => respuesta(200, LISTA) });
    await cargar();
    expect(pantalla.querySelectorAll('h1')).toHaveLength(1);
    expect(pantalla.querySelector('h1')?.textContent).toBe('Pacientes');
    expect(urls(llamadas)).toEqual(['GET /api/pacientes']);
    const items = pantalla.querySelectorAll('.bp-item');
    expect(items).toHaveLength(2);
    expect(items[0]?.textContent).toContain('Ejemplo, Ana');
    expect(items[1]?.textContent).toContain('Muestra, Carla');
  });

  it('buscar por apellido consulta con q y reemplaza la lista', async () => {
    const { llamadas } = stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'GET /api/pacientes?q=Muestra': () => respuesta(200, [CARLA]),
    });
    await cargar();
    escribir('Buscar por DNI o apellido', 'Muestra');
    pantalla.querySelector('form[role="search"]')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await asentar();
    expect(urls(llamadas)).toContain('GET /api/pacientes?q=Muestra');
    expect(pantalla.querySelectorAll('.bp-item')).toHaveLength(1);
  });

  it('sin detalle ni formulario visibles al entrar', async () => {
    stubServidor({ 'GET /api/pacientes': () => respuesta(200, LISTA) });
    await cargar();
    expect(pantalla.querySelector<HTMLElement>('.pac-detalle')?.hidden).toBe(true);
    expect(pantalla.querySelector<HTMLElement>('.pac-form')?.hidden).toBe(true);
  });
});

describe('pantalla pacientes: detalle', () => {
  it('seleccionar pide GET /api/pacientes/{id} y muestra el contacto de solo lectura', async () => {
    const { llamadas } = stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'GET /api/pacientes/1': () => respuesta(200, ANA),
    });
    await cargar();
    await clic('Ver Ejemplo, Ana');
    expect(urls(llamadas)).toContain('GET /api/pacientes/1');
    const d = detalle();
    expect(d.hidden).toBe(false);
    expect(d.textContent).toContain('Ejemplo, Ana');
    expect(d.textContent).toContain('99.999.999');
    expect(d.textContent).toContain('paciente@ejemplo.com');
    expect(d.textContent).toContain('+54 9 11 0000-0000');
    expect(d.textContent).toContain('Contacto del último turno (solo lectura)');
    expect(d.querySelectorAll('input, select, textarea')).toHaveLength(0);
  });

  it('el foco pasa al titulo del detalle', async () => {
    stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'GET /api/pacientes/1': () => respuesta(200, ANA),
    });
    await cargar();
    await clic('Ver Ejemplo, Ana');
    const titulo = detalle().querySelector('h2');
    expect(titulo?.getAttribute('tabindex')).toBe('-1');
    expect(document.activeElement).toBe(titulo);
  });

  it('con ultimoContacto null indica que no hay datos de contacto', async () => {
    stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'GET /api/pacientes/3': () => respuesta(200, CARLA),
    });
    await cargar();
    await clic('Ver Muestra, Carla');
    expect(detalle().textContent).toContain('Sin turnos registrados: todavía no hay datos de contacto');
    expect(detalle().textContent).not.toContain('paciente@ejemplo.com');
  });

  it('ofrece editar y no ofrece eliminar', async () => {
    stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'GET /api/pacientes/1': () => respuesta(200, ANA),
    });
    await cargar();
    await clic('Ver Ejemplo, Ana');
    expect(boton(detalle(), 'Editar')).toBeDefined();
    const textos = Array.from(pantalla.querySelectorAll('button')).map((b) => (b.getAttribute('aria-label') ?? b.textContent ?? '').toLowerCase());
    expect(textos.some((t) => t.includes('eliminar') || t.includes('borrar'))).toBe(false);
  });

  it('404 al pedir el detalle avisa, oculta el detalle y recarga la busqueda', async () => {
    const { llamadas } = stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'GET /api/pacientes/1': () => respuesta(404, errorApi(404, 'RECURSO_NO_ENCONTRADO', 'No existe', '/api/pacientes/1')),
    });
    await cargar();
    await clic('Ver Ejemplo, Ana');
    expect(alertaGeneral()).toBe('El paciente ya no existe. Se actualizó la lista.');
    expect(detalle().hidden).toBe(true);
    expect(urls(llamadas).filter((u) => u === 'GET /api/pacientes')).toHaveLength(2);
  });
});

describe('pantalla pacientes: alta', () => {
  const body = ejemplo('pacientes-crear.request');

  it('Nuevo paciente abre el formulario sin email ni telefono', async () => {
    stubServidor({ 'GET /api/pacientes': () => respuesta(200, LISTA) });
    await cargar();
    await clic('Nuevo paciente');
    const form = pantalla.querySelector<HTMLElement>('.pac-form');
    expect(form?.hidden).toBe(false);
    expect(form?.querySelector('legend')?.textContent).toBe('Nuevo paciente');
    const labels = Array.from(form?.querySelectorAll('label') ?? []).map((l) => l.textContent);
    expect(labels).toEqual(['Nombre', 'Apellido', 'DNI']);
    expect(document.activeElement).toBe(campoPorLabel(pantalla, 'Nombre'));
  });

  it('alta valida: POST con valores recortados, aviso, detalle y lista refrescada', async () => {
    const { llamadas } = stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'POST /api/pacientes': () => respuesta(201, CARLA),
    });
    await cargar();
    await clic('Nuevo paciente');
    escribir('Nombre', '  Carla ');
    escribir('Apellido', 'Muestra ');
    escribir('DNI', ' 99.999.997 ');
    await enviarFormulario();
    expect(llamadas.find((l) => l.metodo === 'POST')?.body).toEqual(body);
    expect(estadoGeneral()).toBe('Paciente creado');
    expect(detalle().hidden).toBe(false);
    expect(detalle().textContent).toContain('99.999.997');
    expect(pantalla.querySelector<HTMLElement>('.pac-form')?.hidden).toBe(true);
    expect(urls(llamadas).filter((u) => u === 'GET /api/pacientes')).toHaveLength(2);
  });

  it('validacion local: no llama a la API y marca cada campo', async () => {
    const { llamadas } = stubServidor({ 'GET /api/pacientes': () => respuesta(200, LISTA) });
    await cargar();
    await clic('Nuevo paciente');
    escribir('Nombre', '   ');
    escribir('Apellido', 'Muestra');
    escribir('DNI', '99-999-997');
    await enviarFormulario();
    expect(llamadas.some((l) => l.metodo === 'POST')).toBe(false);
    expect(campoPorLabel(pantalla, 'Nombre').getAttribute('aria-invalid')).toBe('true');
    expect(campoPorLabel(pantalla, 'DNI').getAttribute('aria-invalid')).toBe('true');
    expect(campoPorLabel(pantalla, 'Apellido').hasAttribute('aria-invalid')).toBe(false);
  });

  it('acepta DNI de 7 digitos', async () => {
    const { llamadas } = stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'POST /api/pacientes': () => respuesta(201, CARLA),
    });
    await cargar();
    await clic('Nuevo paciente');
    escribir('Nombre', 'Carla');
    escribir('Apellido', 'Muestra');
    escribir('DNI', '9999999');
    await enviarFormulario();
    expect(llamadas.find((l) => l.metodo === 'POST')?.body).toMatchObject({ dni: '9999999' });
  });

  it('cancelar el alta cierra el formulario sin llamar a la API', async () => {
    const { llamadas } = stubServidor({ 'GET /api/pacientes': () => respuesta(200, LISTA) });
    await cargar();
    const nuevo = boton(pantalla, 'Nuevo paciente');
    nuevo.click();
    await clic('Cancelar');
    expect(pantalla.querySelector<HTMLElement>('.pac-form')?.hidden).toBe(true);
    expect(llamadas.some((l) => l.metodo !== 'GET')).toBe(false);
    expect(document.activeElement).toBe(nuevo);
  });
});

describe('pantalla pacientes: edicion', () => {
  const bodyEdicion = ejemplo('pacientes-actualizar.request');

  async function abrirEdicion(): Promise<ReturnType<typeof stubServidor>> {
    const servidor = stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'GET /api/pacientes/3': () => respuesta(200, CARLA),
      'PUT /api/pacientes/3': () => respuesta(200, ejemplo('pacientes-actualizar.200')),
    });
    await cargar();
    await clic('Ver Muestra, Carla');
    await clic('Editar');
    return servidor;
  }

  it('abre el formulario precargado con foco en Nombre', async () => {
    await abrirEdicion();
    expect(pantalla.querySelector('.pac-form legend')?.textContent).toBe('Editar paciente');
    expect(campoPorLabel(pantalla, 'Nombre').value).toBe('Carla');
    expect(campoPorLabel(pantalla, 'Apellido').value).toBe('Muestra');
    expect(campoPorLabel(pantalla, 'DNI').value).toBe('99999997');
    expect(document.activeElement).toBe(campoPorLabel(pantalla, 'Nombre'));
  });

  it('guarda con PUT, avisa, actualiza el detalle y refresca la lista', async () => {
    const { llamadas } = await abrirEdicion();
    escribir('Apellido', 'Muestra Prueba');
    escribir('DNI', '99.999.997');
    await enviarFormulario();
    expect(llamadas.find((l) => l.metodo === 'PUT')?.body).toEqual(bodyEdicion);
    expect(estadoGeneral()).toBe('Cambios guardados');
    expect(detalle().textContent).toContain('Muestra Prueba, Carla');
    expect(pantalla.querySelector<HTMLElement>('.pac-form')?.hidden).toBe(true);
    expect(urls(llamadas).filter((u) => u === 'GET /api/pacientes')).toHaveLength(2);
  });

  it('cancelar no llama a la API, conserva el detalle y devuelve el foco a Editar', async () => {
    const { llamadas } = await abrirEdicion();
    escribir('Apellido', 'Otro');
    await clic('Cancelar');
    expect(llamadas.some((l) => l.metodo === 'PUT')).toBe(false);
    expect(pantalla.querySelector<HTMLElement>('.pac-form')?.hidden).toBe(true);
    expect(detalle().textContent).toContain('Muestra, Carla');
    expect(document.activeElement).toBe(boton(detalle(), 'Editar'));
  });

  it('404 al guardar avisa, cierra detalle y formulario y recarga la busqueda', async () => {
    const { llamadas } = stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'GET /api/pacientes/3': () => respuesta(200, CARLA),
      'PUT /api/pacientes/3': () => respuesta(404, errorApi(404, 'RECURSO_NO_ENCONTRADO', 'No existe', '/api/pacientes/3')),
    });
    await cargar();
    await clic('Ver Muestra, Carla');
    await clic('Editar');
    await enviarFormulario();
    expect(alertaGeneral()).toBe('El paciente ya no existe. Se actualizó la lista.');
    expect(detalle().hidden).toBe(true);
    expect(pantalla.querySelector<HTMLElement>('.pac-form')?.hidden).toBe(true);
    expect(urls(llamadas).filter((u) => u === 'GET /api/pacientes')).toHaveLength(2);
  });
});

describe('pantalla pacientes: errores de la API', () => {
  it('422 DNI_DUPLICADO en el alta: error en el campo DNI, foco y valores conservados', async () => {
    stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'POST /api/pacientes': () => respuesta(422, ejemplo('pacientes-crear.422-dni-duplicado')),
    });
    await cargar();
    await clic('Nuevo paciente');
    escribir('Nombre', 'Carla');
    escribir('Apellido', 'Muestra');
    escribir('DNI', '99.999.997');
    await enviarFormulario();
    const dni = campoPorLabel(pantalla, 'DNI');
    expect(pantalla.querySelector('#pac-dni-error')?.textContent).toBe('Ya existe un paciente con ese DNI.');
    expect(dni.getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(dni);
    expect(dni.value).toBe('99.999.997');
    expect(campoPorLabel(pantalla, 'Nombre').value).toBe('Carla');
    expect(alertaGeneral()).toBe('');
    expect(pantalla.querySelector<HTMLElement>('.pac-form')?.hidden).toBe(false);
  });

  it('422 DNI_DUPLICADO en la edicion: error en el campo y el detalle no cambia', async () => {
    stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'GET /api/pacientes/3': () => respuesta(200, CARLA),
      'PUT /api/pacientes/3': () => respuesta(422, ejemplo('pacientes-actualizar.422-dni-duplicado')),
    });
    await cargar();
    await clic('Ver Muestra, Carla');
    await clic('Editar');
    escribir('Apellido', 'Muestra Prueba');
    await enviarFormulario();
    expect(pantalla.querySelector('#pac-dni-error')?.textContent).toBe('Ya existe un paciente con ese DNI.');
    expect(campoPorLabel(pantalla, 'DNI').getAttribute('aria-invalid')).toBe('true');
    expect(detalle().textContent).toContain('Muestra, Carla');
    expect(detalle().textContent).not.toContain('Muestra Prueba');
  });

  it('400 VALIDACION con detalle para apellido lo muestra junto al campo', async () => {
    stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'POST /api/pacientes': () =>
        respuesta(
          400,
          errorApi(400, 'VALIDACION', 'Datos inválidos', '/api/pacientes', [{ campo: 'apellido', mensaje: 'El apellido no es válido.' }]),
        ),
    });
    await cargar();
    await clic('Nuevo paciente');
    escribir('Nombre', 'Carla');
    escribir('Apellido', 'Muestra');
    escribir('DNI', '99999997');
    await enviarFormulario();
    expect(pantalla.querySelector('#pac-apellido-error')?.textContent).toBe('El apellido no es válido.');
    expect(campoPorLabel(pantalla, 'Apellido').getAttribute('aria-invalid')).toBe('true');
    expect(campoPorLabel(pantalla, 'Nombre').value).toBe('Carla');
  });

  it('otros errores van a la alerta general con mensaje generico', async () => {
    stubServidor({
      'GET /api/pacientes': () => respuesta(200, LISTA),
      'POST /api/pacientes': () => respuesta(500, errorApi(500, 'ERROR_INTERNO', 'NullPointerException en Servicio', '/api/pacientes')),
    });
    await cargar();
    await clic('Nuevo paciente');
    escribir('Nombre', 'Carla');
    escribir('Apellido', 'Muestra');
    escribir('DNI', '99999997');
    await enviarFormulario();
    expect(alertaGeneral()).toBe('No se pudo completar la operación. Intentá de nuevo en unos minutos.');
    expect(pantalla.textContent).not.toContain('NullPointerException');
    expect(pantalla.querySelector<HTMLElement>('.pac-form')?.hidden).toBe(false);
  });
});

describe('pantalla pacientes: accesibilidad', () => {
  it('cada campo tiene label y una region de error asociada con aria-describedby', async () => {
    stubServidor({ 'GET /api/pacientes': () => respuesta(200, LISTA) });
    await cargar();
    await clic('Nuevo paciente');
    for (const [label, id] of [
      ['Nombre', 'pac-nombre'],
      ['Apellido', 'pac-apellido'],
      ['DNI', 'pac-dni'],
    ] as const) {
      const campo = campoPorLabel(pantalla, label);
      expect(campo.id).toBe(id);
      expect(campo.getAttribute('aria-describedby')).toBe(id + '-error');
      expect(pantalla.querySelector('#' + id + '-error')).not.toBeNull();
    }
    expect(campoPorLabel(pantalla, 'DNI').getAttribute('inputmode')).toBe('numeric');
  });

  it('hay regiones status y alert para avisos y para el buscador', async () => {
    stubServidor({ 'GET /api/pacientes': () => respuesta(200, LISTA) });
    await cargar();
    expect(pantalla.querySelectorAll('[role="status"]').length).toBeGreaterThanOrEqual(2);
    expect(pantalla.querySelectorAll('[role="alert"]').length).toBeGreaterThanOrEqual(2);
    expect(pantalla.querySelector('form[role="search"]')).not.toBeNull();
  });
});
