import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi, ErrorConexion } from '../../api/cliente';
import type { ApiError, Disponibilidad } from '../../api/types';
import { ejemplo } from '../configuracion/ejemplos.testutil';
import { obtenerDisponibilidadAdmin } from './acciones.api';
import { crearSelectorHorario, type SelectorHorario } from './selector-horario';

vi.mock('./acciones.api', () => ({ obtenerDisponibilidadAdmin: vi.fn() }));

const consultar = vi.mocked(obtenerDisponibilidadAdmin);
const disponibilidad = ejemplo('disponibilidad-privada.200') as Disponibilidad;
const sinHorarios: Disponibilidad = {
  prestacion: disponibilidad.prestacion,
  dias: [{ fecha: '2026-12-01', horarios: [] }],
};

let selector: SelectorHorario;
let elegidos: Array<string | null>;

function montar(prestacionId: number | null = 1): HTMLElement {
  elegidos = [];
  selector = crearSelectorHorario({
    prefijo: 'sel',
    prestacionId,
    ahora: () => new Date('2026-12-01T12:00:00Z'),
    alElegir: (instante) => elegidos.push(instante),
  });
  document.body.replaceChildren(selector.raiz);
  return selector.raiz;
}

function dia(): HTMLInputElement {
  return document.getElementById('sel-dia') as HTMLInputElement;
}

function verHorarios(): void {
  [...document.querySelectorAll('button')].find((b) => b.textContent === 'Ver horarios')?.click();
}

function radios(): HTMLInputElement[] {
  return [...document.querySelectorAll<HTMLInputElement>('fieldset input[type="radio"]')];
}

function errorApi(nombre: string): ErrorApi {
  const cuerpo = ejemplo(nombre) as ApiError;
  return new ErrorApi(cuerpo.status, cuerpo.mensaje, cuerpo.codigo, cuerpo.detalles, cuerpo);
}

beforeEach(() => {
  consultar.mockReset();
  consultar.mockResolvedValue(disponibilidad);
});

afterEach(() => {
  document.body.innerHTML = '';
});

describe('selector de horario', () => {
  it('asocia el label "Día" a un input date con min igual a hoy en Buenos Aires', () => {
    montar();
    const label = [...document.querySelectorAll('label')].find((l) => l.textContent === 'Día');
    expect(label?.control).toBe(dia());
    expect(dia().type).toBe('date');
    expect(dia().min).toBe('2026-12-01');
    expect(dia().max).toBe('');
  });

  it('el min usa el día de Buenos Aires aunque en UTC ya sea otro día', () => {
    elegidos = [];
    const otro = crearSelectorHorario({
      prefijo: 'otro',
      prestacionId: 1,
      ahora: () => new Date('2026-12-02T01:00:00Z'),
      alElegir: () => undefined,
    });
    document.body.replaceChildren(otro.raiz);
    expect((document.getElementById('otro-dia') as HTMLInputElement).min).toBe('2026-12-01');
  });

  it('"Ver horarios" consulta con la prestación y el día y muestra radios con el instante exacto', async () => {
    montar();
    dia().value = '2026-12-01';
    verHorarios();
    await vi.waitFor(() => expect(radios()).toHaveLength(2));
    expect(consultar).toHaveBeenCalledWith(1, '2026-12-01');
    expect(document.querySelector('fieldset legend')?.textContent).toBe('Horarios libres');
    expect(radios().map((r) => r.value)).toEqual(['2026-12-01T09:00:00-03:00', '2026-12-01T09:30:00-03:00']);
    expect(radios().map((r) => r.labels?.[0]?.textContent)).toEqual(['09:00', '09:30']);
  });

  it('elegir un horario llama a alElegir con el instante y lo expone', async () => {
    montar();
    verHorarios();
    await vi.waitFor(() => expect(radios()).toHaveLength(2));
    radios()[1]?.click();
    expect(elegidos).toEqual(['2026-12-01T09:30:00-03:00']);
    expect(selector.elegido()).toBe('2026-12-01T09:30:00-03:00');
  });

  it('un día sin horarios lo dice en una región de estado', async () => {
    consultar.mockResolvedValue(sinHorarios);
    montar();
    verHorarios();
    await vi.waitFor(() => {
      expect(document.querySelector('[role="status"]')?.textContent).toBe('No hay horarios libres ese día');
    });
    expect(radios()).toHaveLength(0);
  });

  it('refrescar pide de nuevo el mismo día y limpia la elección', async () => {
    montar();
    verHorarios();
    await vi.waitFor(() => expect(radios()).toHaveLength(2));
    radios()[0]?.click();
    await selector.refrescar();
    expect(consultar).toHaveBeenCalledTimes(2);
    expect(consultar).toHaveBeenLastCalledWith(1, '2026-12-01');
    expect(selector.elegido()).toBeNull();
    expect(elegidos.at(-1)).toBeNull();
    expect(radios().every((r) => !r.checked)).toBe(true);
  });

  it('cambiarPrestacion descarta la elección y vuelve a pedir con la nueva prestación', async () => {
    montar();
    verHorarios();
    await vi.waitFor(() => expect(radios()).toHaveLength(2));
    radios()[0]?.click();
    await selector.cambiarPrestacion(2);
    expect(selector.elegido()).toBeNull();
    expect(consultar).toHaveBeenLastCalledWith(2, '2026-12-01');
  });

  it('sin prestación no consulta y lo avisa', () => {
    montar(null);
    verHorarios();
    expect(consultar).not.toHaveBeenCalled();
    expect(document.querySelector('[role="status"]')?.textContent).toBe('Elegí una prestación para ver los horarios.');
  });

  it('descarta una respuesta fuera de orden', async () => {
    let lenta: (d: Disponibilidad) => void = () => undefined;
    consultar.mockReturnValueOnce(new Promise<Disponibilidad>((resolver) => (lenta = resolver)));
    consultar.mockResolvedValueOnce(sinHorarios);
    montar();
    verHorarios();
    verHorarios();
    await vi.waitFor(() => {
      expect(document.querySelector('[role="status"]')?.textContent).toBe('No hay horarios libres ese día');
    });
    lenta(disponibilidad);
    await Promise.resolve();
    await Promise.resolve();
    expect(radios()).toHaveLength(0);
  });

  it('un error de conexión muestra el mensaje y "Reintentar" vuelve a pedir', async () => {
    consultar.mockRejectedValueOnce(new ErrorConexion());
    montar();
    verHorarios();
    await vi.waitFor(() => expect(document.querySelector('[role="alert"]')?.textContent).toContain('conectarnos'));
    [...document.querySelectorAll('button')].find((b) => b.textContent === 'Reintentar')?.click();
    await vi.waitFor(() => expect(radios()).toHaveLength(2));
    expect(document.querySelector('[role="alert"]')?.textContent).toBe('');
  });

  it('un error de la API muestra el mensaje propio, no el del servidor', async () => {
    const error = errorApi('disponibilidad-privada.422-prestacion-inactiva');
    consultar.mockRejectedValueOnce(error);
    montar();
    verHorarios();
    await vi.waitFor(() => expect(document.querySelector('[role="alert"]')?.textContent).not.toBe(''));
    expect(document.querySelector('[role="alert"]')?.textContent).not.toBe(error.message);
  });
});
