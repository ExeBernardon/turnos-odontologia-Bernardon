import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Paciente, Prestacion, Turno } from '../../api/types';
import { ejemplo } from '../configuracion/ejemplos.testutil';
import { respuesta, stubServidor } from '../configuracion/servidor.testutil';
import { montarAltaManual } from './alta-manual';

const BRUNO: Paciente = {
  id: 2,
  nombre: 'Bruno',
  apellido: 'Prueba',
  dni: '99999998',
  ultimoContacto: { email: 'bruno@ejemplo.com', telefono: '+54 9 11 0000-0001' },
};
const CARLA: Paciente = { id: 3, nombre: 'Carla', apellido: 'Muestra', dni: '99999997', ultimoContacto: null };
const PRESTACIONES = ejemplo('prestaciones-listar.200') as Prestacion[];

let panel: HTMLElement;
let alCrear: ReturnType<typeof vi.fn<(turno: Turno) => void>>;
let anunciar: ReturnType<typeof vi.fn<(texto: string) => void>>;
let alCerrar: ReturnType<typeof vi.fn<() => void>>;

async function asentar(): Promise<void> {
  await vi.waitFor(() => undefined);
  await new Promise((r) => setTimeout(r, 0));
}

function abrir(): void {
  document.body.innerHTML = '<div id="panel"></div>';
  panel = document.getElementById('panel') as HTMLElement;
  alCrear = vi.fn<(turno: Turno) => void>();
  anunciar = vi.fn<(texto: string) => void>();
  alCerrar = vi.fn<() => void>();
  montarAltaManual(panel, { ahora: () => new Date('2026-12-01T12:00:00Z'), alCrear, anunciar, alCerrar });
}

function porLabel(texto: string): HTMLInputElement | HTMLSelectElement {
  const label = [...panel.querySelectorAll('label')].find((l) => l.textContent === texto);
  expect(label?.control, 'campo ' + texto).toBeTruthy();
  return label?.control as HTMLInputElement | HTMLSelectElement;
}

function boton(nombre: string): HTMLButtonElement {
  const b = [...panel.querySelectorAll('button')].find((x) => (x.getAttribute('aria-label') ?? x.textContent) === nombre);
  expect(b, 'botón ' + nombre).toBeInstanceOf(HTMLButtonElement);
  return b as HTMLButtonElement;
}

async function buscarYElegir(q: string, nombreBoton: string): Promise<void> {
  const campo = porLabel('Buscar por DNI o apellido');
  campo.value = q;
  panel.querySelector('form[role="search"]')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  await vi.waitFor(() => expect(panel.querySelector('.bp-lista')?.children.length).toBeGreaterThan(0));
  boton(nombreBoton).click();
}

const SERVIDOR_BASE = {
  'GET /api/prestaciones': () => respuesta(200, PRESTACIONES),
  'GET /api/pacientes?q=Prueba': () => respuesta(200, [BRUNO]),
  'GET /api/pacientes?q=Muestra': () => respuesta(200, [CARLA]),
};

beforeEach(() => {
  stubServidor(SERVIDOR_BASE);
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});

describe('alta manual: elección del paciente', () => {
  it('muestra un h2 "Nuevo turno" con el foco y el fieldset "Paciente" con sus dos radios', async () => {
    abrir();
    const titulo = panel.querySelector('h2');
    expect(titulo?.textContent).toBe('Nuevo turno');
    expect(document.activeElement).toBe(titulo);
    expect(panel.querySelector('section')?.getAttribute('aria-labelledby')).toBe(titulo?.id);
    const grupo = [...panel.querySelectorAll('fieldset')].find((f) => f.querySelector('legend')?.textContent === 'Paciente');
    const radios = [...(grupo?.querySelectorAll<HTMLInputElement>('input[type="radio"]') ?? [])];
    expect(radios.map((r) => r.labels?.[0]?.textContent)).toEqual(['Paciente existente', 'Paciente nuevo']);
    expect(radios[0]?.checked).toBe(true);
    await asentar();
  });

  it('con "existente" usa el buscador con "Elegir" y precarga el contacto editable', async () => {
    abrir();
    await asentar();
    await buscarYElegir('Prueba', 'Elegir Prueba, Bruno');
    expect(panel.textContent).toContain('Prueba, Bruno');
    expect(porLabel('Email').value).toBe('bruno@ejemplo.com');
    expect(porLabel('Teléfono').value).toBe('+54 9 11 0000-0001');
    const email = porLabel('Email');
    email.value = 'otro@ejemplo.com';
    expect(email.value).toBe('otro@ejemplo.com');
    expect(panel.textContent).toContain('último turno');
  });

  it('un paciente sin último contacto deja los campos vacíos con la indicación', async () => {
    abrir();
    await asentar();
    await buscarYElegir('Muestra', 'Elegir Muestra, Carla');
    expect(porLabel('Email').value).toBe('');
    expect(porLabel('Teléfono').value).toBe('');
    expect(panel.textContent).toContain('no tiene turnos anteriores');
  });

  it('"Cambiar paciente" vuelve al buscador', async () => {
    abrir();
    await asentar();
    await buscarYElegir('Prueba', 'Elegir Prueba, Bruno');
    boton('Cambiar paciente').click();
    expect(panel.querySelector('form[role="search"]')).not.toBeNull();
    expect(panel.textContent).not.toContain('Cambiar paciente');
  });
});

// ---------------------------------------------------------------- envío (6.2)

const ANA: Paciente = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Ejemplo',
  dni: '99999999',
  ultimoContacto: { email: 'paciente@ejemplo.com', telefono: '+54 9 11 0000-0000' },
};
const REQ_EXISTENTE = ejemplo('turnos-crear.request-paciente-existente');
const REQ_NUEVO = ejemplo('turnos-crear.request-paciente-nuevo');
const TURNO_CREADO = ejemplo('turnos-crear.201') as Turno;
const DISPONIBILIDAD_DIC_4 = {
  prestacion: { id: 1, nombre: 'Consulta', duracionMinutos: 30 },
  dias: [{ fecha: '2026-12-04', horarios: ['2026-12-04T15:00:00-03:00', '2026-12-04T16:00:00-03:00'] }],
};
const RUTA_DIA_4 = 'GET /api/disponibilidad?prestacionId=1&desde=2026-12-04&hasta=2026-12-04';

function servidor(extra: Parameters<typeof stubServidor>[0] = {}): ReturnType<typeof stubServidor> {
  return stubServidor({
    ...SERVIDOR_BASE,
    'GET /api/pacientes?q=Ejemplo': () => respuesta(200, [ANA]),
    [RUTA_DIA_4]: () => respuesta(200, DISPONIBILIDAD_DIC_4),
    ...extra,
  });
}

function elegirPrestacion(id: string): void {
  const select = porLabel('Prestación') as HTMLSelectElement;
  select.value = id;
  select.dispatchEvent(new Event('change', { bubbles: true }));
}

async function verHorarios(fecha: string, cantidad: number): Promise<void> {
  porLabel('Día').value = fecha;
  boton('Ver horarios').click();
  await vi.waitFor(() => expect(panel.querySelectorAll('input[name$="-horario"]')).toHaveLength(cantidad));
}

function elegirHorario(texto: string): void {
  porLabel(texto).click();
}

function enviar(): void {
  boton('Cargar turno').click();
}

function llenarNuevo(valores: { nombre: string; apellido: string; dni: string }): void {
  porLabel('Paciente nuevo').click();
  porLabel('Nombre').value = valores.nombre;
  porLabel('Apellido').value = valores.apellido;
  porLabel('DNI').value = valores.dni;
}

function posts(llamadas: ReturnType<typeof stubServidor>['llamadas']): typeof llamadas {
  return llamadas.filter((l) => l.metodo === 'POST');
}

describe('alta manual: envío', () => {
  it('con datos de paciente nuevo inválidos muestra el error junto al campo sin llamar a la API', async () => {
    const { llamadas } = servidor();
    abrir();
    await asentar();
    llenarNuevo({ nombre: '', apellido: 'Muestra', dni: 'abc' });
    porLabel('Email').value = 'carla@ejemplo.com';
    porLabel('Teléfono').value = '+54 9 11 0000-0002';
    elegirPrestacion('1');
    await verHorarios('2026-12-04', 2);
    elegirHorario('16:00');
    enviar();
    expect(document.getElementById('alta-turno-nombre-error')?.textContent).toContain('obligatorio');
    expect(porLabel('DNI').getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(porLabel('Nombre'));
    expect(posts(llamadas)).toHaveLength(0);
  });

  it('con un contacto inválido lo marca en el campo y no envía', async () => {
    const { llamadas } = servidor();
    abrir();
    await asentar();
    llenarNuevo({ nombre: 'Carla', apellido: 'Muestra', dni: '99.999.997' });
    porLabel('Email').value = 'sin-arroba';
    porLabel('Teléfono').value = '12345';
    elegirPrestacion('1');
    await verHorarios('2026-12-04', 2);
    elegirHorario('16:00');
    enviar();
    expect(porLabel('Email').getAttribute('aria-invalid')).toBe('true');
    expect(porLabel('Teléfono').getAttribute('aria-invalid')).toBe('true');
    expect(posts(llamadas)).toHaveLength(0);
  });

  it('solo ofrece prestaciones activas y cambiarla limpia el horario elegido', async () => {
    servidor({ 'GET /api/disponibilidad?prestacionId=2&desde=2026-12-04&hasta=2026-12-04': () => respuesta(200, DISPONIBILIDAD_DIC_4) });
    abrir();
    await vi.waitFor(() => expect((porLabel('Prestación') as HTMLSelectElement).options.length).toBeGreaterThan(1));
    const nombres = [...(porLabel('Prestación') as HTMLSelectElement).options].map((o) => o.textContent);
    expect(nombres).toEqual(['Elegí una prestación', 'Consulta (30 min)', 'Limpieza (45 min)']);
    elegirPrestacion('1');
    await verHorarios('2026-12-04', 2);
    elegirHorario('15:00');
    elegirPrestacion('2');
    await vi.waitFor(() => expect(panel.querySelectorAll('input[name$="-horario"]:checked')).toHaveLength(0));
  });

  it('sin horario elegido no envía y avisa', async () => {
    const { llamadas } = servidor();
    abrir();
    await asentar();
    await buscarYElegir('Ejemplo', 'Elegir Ejemplo, Ana');
    elegirPrestacion('1');
    enviar();
    expect(panel.querySelector('.cfg-avisos [role="alert"]')?.textContent).toContain('horario');
    expect(posts(llamadas)).toHaveLength(0);
  });

  it('sin paciente existente elegido no envía y avisa', async () => {
    const { llamadas } = servidor();
    abrir();
    await asentar();
    elegirPrestacion('1');
    await verHorarios('2026-12-04', 2);
    elegirHorario('15:00');
    porLabel('Email').value = 'paciente@ejemplo.com';
    porLabel('Teléfono').value = '+54 9 11 0000-0000';
    enviar();
    expect(panel.querySelector('.cfg-avisos [role="alert"]')?.textContent).toContain('paciente');
    expect(posts(llamadas)).toHaveLength(0);
  });

  it('el envío con paciente existente manda el body del ejemplo, anuncia, notifica y cierra el panel', async () => {
    const { llamadas } = servidor({ 'POST /api/turnos': () => respuesta(201, TURNO_CREADO) });
    abrir();
    await asentar();
    await buscarYElegir('Ejemplo', 'Elegir Ejemplo, Ana');
    elegirPrestacion('1');
    await verHorarios('2026-12-04', 2);
    elegirHorario('15:00');
    enviar();
    await vi.waitFor(() => expect(alCrear).toHaveBeenCalledTimes(1));
    expect(posts(llamadas)[0]?.body).toEqual(REQ_EXISTENTE);
    expect(anunciar).toHaveBeenCalledWith('Turno cargado: pendiente de confirmación');
    expect(alCrear.mock.calls[0]?.[0].id).toBe(105);
    expect(alCerrar).toHaveBeenCalledTimes(1);
    expect(panel.children).toHaveLength(0);
  });

  it('el envío con paciente nuevo manda el body del ejemplo y anuncia según el estado de la respuesta', async () => {
    const { llamadas } = servidor({ 'POST /api/turnos': () => respuesta(201, { ...TURNO_CREADO, estado: 'CONFIRMADO' }) });
    abrir();
    await asentar();
    llenarNuevo({ nombre: 'Carla', apellido: 'Muestra', dni: '99.999.997' });
    porLabel('Email').value = 'carla@ejemplo.com';
    porLabel('Teléfono').value = '+54 9 11 0000-0002';
    elegirPrestacion('1');
    await verHorarios('2026-12-04', 2);
    elegirHorario('16:00');
    enviar();
    await vi.waitFor(() => expect(alCrear).toHaveBeenCalledTimes(1));
    expect(posts(llamadas)[0]?.body).toEqual(REQ_NUEVO);
    expect(anunciar).toHaveBeenCalledWith('Turno cargado: confirmado');
  });

  it('deshabilita el botón de enviar mientras espera la respuesta y evita el doble envío', async () => {
    let resolver: (r: Response) => void = () => undefined;
    const { llamadas } = servidor({
      'POST /api/turnos': () => new Promise<Response>((r) => (resolver = r)),
    });
    abrir();
    await asentar();
    await buscarYElegir('Ejemplo', 'Elegir Ejemplo, Ana');
    elegirPrestacion('1');
    await verHorarios('2026-12-04', 2);
    elegirHorario('15:00');
    enviar();
    await vi.waitFor(() => expect(boton('Cargar turno').disabled).toBe(true));
    boton('Cargar turno').click();
    expect(posts(llamadas)).toHaveLength(1);
    resolver(respuesta(201, TURNO_CREADO));
    await vi.waitFor(() => expect(alCrear).toHaveBeenCalled());
  });
});

// ---------------------------------------------------------------- errores del servidor (6.3)

function alerta(): string {
  return panel.querySelector('.cfg-avisos [role="alert"]')?.textContent ?? '';
}

/** Llena un alta de paciente existente lista para enviar y la envía; el POST responde con `resp`. */
async function enviarConRespuesta(
  resp: () => Response,
  esperarAlerta = true,
): Promise<ReturnType<typeof stubServidor>> {
  const stub = servidor({ 'POST /api/turnos': resp });
  abrir();
  await asentar();
  await buscarYElegir('Ejemplo', 'Elegir Ejemplo, Ana');
  elegirPrestacion('1');
  await verHorarios('2026-12-04', 2);
  elegirHorario('15:00');
  enviar();
  await vi.waitFor(() => expect(posts(stub.llamadas)).toHaveLength(1));
  if (esperarAlerta) {
    await vi.waitFor(() => expect(alerta()).not.toBe(''));
  } else {
    await asentar();
  }
  return stub;
}

describe('alta manual: errores del servidor', () => {
  it.each([
    ['409 HORARIO_OCUPADO', 409, 'turnos-crear.409-horario-ocupado'],
    ['422 HORARIO_NO_DISPONIBLE', 422, 'turnos-crear.422-horario-no-disponible'],
  ])('%s muestra el mensaje y vuelve a consultar los horarios', async (_n, status, nombre) => {
    const { llamadas } = await enviarConRespuesta(() => respuesta(status, ejemplo(nombre)));
    expect(alerta()).toContain('horario');
    await vi.waitFor(() => expect(llamadas.filter((l) => l.url === RUTA_DIA_4.slice(4))).toHaveLength(2));
    expect(alCrear).not.toHaveBeenCalled();
    expect(panel.querySelector('h2')?.textContent).toBe('Nuevo turno');
    expect(porLabel('Email').value).toBe('paciente@ejemplo.com');
  });

  it('422 PRESTACION_INACTIVA muestra el mensaje y vuelve a consultar las prestaciones', async () => {
    const { llamadas } = await enviarConRespuesta(() => respuesta(422, ejemplo('turnos-crear.422-prestacion-inactiva')));
    expect(alerta()).toContain('prestación');
    await vi.waitFor(() => expect(llamadas.filter((l) => l.url === '/api/prestaciones')).toHaveLength(2));
  });

  it('422 DNI_DUPLICADO marca el DNI, lo enfoca y sugiere buscarlo', async () => {
    const stub = servidor({ 'POST /api/turnos': () => respuesta(422, ejemplo('turnos-crear.422-dni-duplicado')) });
    abrir();
    await asentar();
    llenarNuevo({ nombre: 'Carla', apellido: 'Muestra', dni: '99.999.997' });
    porLabel('Email').value = 'carla@ejemplo.com';
    porLabel('Teléfono').value = '+54 9 11 0000-0002';
    elegirPrestacion('1');
    await verHorarios('2026-12-04', 2);
    elegirHorario('16:00');
    enviar();
    await vi.waitFor(() => expect(porLabel('DNI').getAttribute('aria-invalid')).toBe('true'));
    expect(document.activeElement).toBe(porLabel('DNI'));
    expect(document.getElementById('alta-turno-dni-error')?.textContent).toContain('Paciente existente');
    expect(posts(stub.llamadas)).toHaveLength(1);
    expect(porLabel('Nombre').value).toBe('Carla');
  });

  it('400 VALIDACION con emailContacto marca el error junto al email', async () => {
    const cuerpo = {
      ...(ejemplo('error.400-validacion') as object),
      path: '/api/turnos',
      detalles: [{ campo: 'emailContacto', mensaje: 'El email no es válido.' }],
    };
    await enviarConRespuesta(() => respuesta(400, cuerpo), false);
    expect(porLabel('Email').getAttribute('aria-invalid')).toBe('true');
    expect(document.getElementById('alta-turno-emailContacto-error')?.textContent).toBe('El email no es válido.');
    expect(porLabel('Teléfono').value).toBe('+54 9 11 0000-0000');
  });

  it('un error de conexión y un 500 muestran el mensaje genérico y conservan los datos', async () => {
    await enviarConRespuesta(() => respuesta(500, ejemplo('error.500-error-interno')));
    expect(alerta()).toBe('No se pudo completar la operación. Intentá de nuevo en unos minutos.');
    expect(boton('Cargar turno').disabled).toBe(false);
    expect(panel.querySelector('input[name$="-horario"]:checked')).not.toBeNull();
    expect(alCrear).not.toHaveBeenCalled();
  });

  it('un error de red muestra el mensaje de conexión', async () => {
    const stub = servidor({
      'POST /api/turnos': () => {
        throw new TypeError('network');
      },
    });
    abrir();
    await asentar();
    await buscarYElegir('Ejemplo', 'Elegir Ejemplo, Ana');
    elegirPrestacion('1');
    await verHorarios('2026-12-04', 2);
    elegirHorario('15:00');
    enviar();
    await vi.waitFor(() => expect(alerta()).toContain('conectarnos'));
    expect(posts(stub.llamadas)).toHaveLength(1);
  });
});
