import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Turno } from '../../api/types';
import { ejemplo } from '../configuracion/ejemplos.testutil';
import { respuesta, stubServidor } from '../configuracion/servidor.testutil';
import { montarReprogramar } from './reprogramar';

const TURNO = (ejemplo('turnos-listar.200') as Turno[])[0] as Turno; // 102, Bruno Prueba, Limpieza (id 2), 16:00
const REPROGRAMADO = ejemplo('turnos-reprogramar.200') as Turno; // 18:00 CONFIRMADO
const RUTA_DIA = 'GET /api/disponibilidad?prestacionId=2&desde=2026-12-01&hasta=2026-12-01';
const DISPONIBILIDAD = {
  prestacion: { id: 2, nombre: 'Limpieza', duracionMinutos: 45 },
  dias: [{ fecha: '2026-12-01', horarios: ['2026-12-01T18:00:00-03:00', '2026-12-01T18:30:00-03:00'] }],
};

let panel: HTMLElement;
let anunciar: ReturnType<typeof vi.fn<(texto: string) => void>>;
let alertar: ReturnType<typeof vi.fn<(texto: string) => void>>;
let alCambiar: ReturnType<typeof vi.fn<() => void>>;
let alCerrar: ReturnType<typeof vi.fn<() => void>>;

function abrir(): void {
  document.body.innerHTML = '<div id="panel"></div>';
  panel = document.getElementById('panel') as HTMLElement;
  anunciar = vi.fn<(texto: string) => void>();
  alertar = vi.fn<(texto: string) => void>();
  alCambiar = vi.fn<() => void>();
  alCerrar = vi.fn<() => void>();
  montarReprogramar(panel, {
    turno: TURNO,
    ahora: () => new Date('2026-12-01T12:00:00Z'),
    anunciar,
    alertar,
    alCambiar,
    alCerrar,
  });
}

function servidor(extra: Parameters<typeof stubServidor>[0] = {}): ReturnType<typeof stubServidor> {
  return stubServidor({ [RUTA_DIA]: () => respuesta(200, DISPONIBILIDAD), ...extra });
}

function boton(prefijo: string): HTMLButtonElement {
  const b = [...panel.querySelectorAll('button')].find((x) => (x.textContent ?? '').startsWith(prefijo));
  expect(b, 'botón ' + prefijo).toBeInstanceOf(HTMLButtonElement);
  return b as HTMLButtonElement;
}

async function elegirHorario(texto: string): Promise<void> {
  boton('Ver horarios').click();
  await vi.waitFor(() => expect(panel.querySelectorAll('input[name$="-horario"]')).toHaveLength(2));
  const label = [...panel.querySelectorAll('label')].find((l) => l.textContent === texto);
  (label?.control as HTMLInputElement).click();
}

function alerta(): string {
  return panel.querySelector('.cfg-avisos [role="alert"]')?.textContent ?? '';
}

function patches(llamadas: ReturnType<typeof stubServidor>['llamadas']): typeof llamadas {
  return llamadas.filter((l) => l.metodo === 'PATCH');
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

afterEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});

describe('reprogramar: presentación y envío', () => {
  it('muestra un h2 con el paciente, la prestación y el horario actual, y le da el foco', () => {
    servidor();
    abrir();
    const titulo = panel.querySelector('h2');
    expect(titulo?.textContent).toBe('Reprogramar turno');
    expect(document.activeElement).toBe(titulo);
    expect(panel.querySelector('section')?.getAttribute('aria-labelledby')).toBe(titulo?.id);
    expect(panel.textContent).toContain('Bruno Prueba');
    expect(panel.textContent).toContain('Limpieza');
    expect(panel.textContent).toContain('martes 1 de diciembre, 16:00');
  });

  it('el selector usa la prestación del turno y el botón nombra el nuevo horario', async () => {
    const { llamadas } = servidor();
    abrir();
    await elegirHorario('18:00');
    expect(llamadas[0]?.url).toBe(RUTA_DIA.slice(4));
    expect(boton('Reprogramar al').textContent).toBe('Reprogramar al martes 1 de diciembre, 18:00');
  });

  it('el 200 anuncia el nuevo horario con el estado de la respuesta y recarga', async () => {
    const { llamadas } = servidor({
      'PATCH /api/turnos/102/reprogramar': () => respuesta(200, { ...REPROGRAMADO, estado: 'PENDIENTE' }),
    });
    abrir();
    await elegirHorario('18:00');
    boton('Reprogramar al').click();
    await vi.waitFor(() => expect(alCambiar).toHaveBeenCalledTimes(1));
    expect(patches(llamadas)[0]?.body).toEqual(ejemplo('turnos-reprogramar.request'));
    expect(anunciar).toHaveBeenCalledWith(
      'Turno reprogramado al martes 1 de diciembre, 18:00: queda pendiente de confirmación',
    );
    expect(alCerrar).toHaveBeenCalledTimes(1);
    expect(panel.children).toHaveLength(0);
  });

  it('con CONFIRMADO anuncia "queda confirmado"', async () => {
    servidor({ 'PATCH /api/turnos/102/reprogramar': () => respuesta(200, REPROGRAMADO) });
    abrir();
    await elegirHorario('18:00');
    boton('Reprogramar al').click();
    await vi.waitFor(() => expect(anunciar).toHaveBeenCalled());
    expect(anunciar.mock.calls[0]?.[0]).toContain('queda confirmado');
  });

  it('sin horario elegido no envía y avisa', async () => {
    const { llamadas } = servidor();
    abrir();
    boton('Reprogramar').click();
    expect(alerta()).toContain('horario');
    expect(patches(llamadas)).toHaveLength(0);
  });
});

describe('reprogramar: errores y cierre', () => {
  it.each([
    ['409', 409, 'turnos-reprogramar.409-horario-ocupado'],
    ['422 HORARIO_NO_DISPONIBLE', 422, 'turnos-reprogramar.422-horario-no-disponible'],
  ])('%s deja el panel abierto, avisa y vuelve a consultar los horarios', async (_n, status, nombre) => {
    const { llamadas } = servidor({ 'PATCH /api/turnos/102/reprogramar': () => respuesta(status, ejemplo(nombre)) });
    abrir();
    await elegirHorario('18:00');
    boton('Reprogramar al').click();
    await vi.waitFor(() => expect(alerta()).toContain('horario'));
    await vi.waitFor(() => expect(llamadas.filter((l) => l.metodo === 'GET')).toHaveLength(2));
    expect(panel.querySelector('h2')?.textContent).toBe('Reprogramar turno');
    expect(alCambiar).not.toHaveBeenCalled();
  });

  it.each([
    ['422 TRANSICION_INVALIDA', 422, 'turnos-reprogramar.422-transicion-invalida'],
    ['404', 404, 'error.404-recurso-no-encontrado'],
  ])('%s avisa en la agenda, cierra el panel y recarga', async (_n, status, nombre) => {
    servidor({ 'PATCH /api/turnos/102/reprogramar': () => respuesta(status, ejemplo(nombre)) });
    abrir();
    await elegirHorario('18:00');
    boton('Reprogramar al').click();
    await vi.waitFor(() => expect(alCambiar).toHaveBeenCalledTimes(1));
    expect(alertar).toHaveBeenCalledTimes(1);
    expect(alertar.mock.calls[0]?.[0]).not.toBe('');
    expect(panel.children).toHaveLength(0);
    expect(alCerrar).toHaveBeenCalledTimes(1);
  });

  it('un 500 conserva el panel con el mensaje genérico', async () => {
    servidor({ 'PATCH /api/turnos/102/reprogramar': () => respuesta(500, ejemplo('error.500-error-interno')) });
    abrir();
    await elegirHorario('18:00');
    boton('Reprogramar al').click();
    await vi.waitFor(() => expect(alerta()).toContain('No se pudo completar'));
    expect(boton('Reprogramar al').disabled).toBe(false);
    expect(alCambiar).not.toHaveBeenCalled();
  });

  it('"Cerrar" no llama a la API y avisa que se cerró', () => {
    const { llamadas } = servidor();
    abrir();
    boton('Cerrar').click();
    expect(llamadas).toHaveLength(0);
    expect(alCerrar).toHaveBeenCalledTimes(1);
    expect(panel.children).toHaveLength(0);
  });
});
