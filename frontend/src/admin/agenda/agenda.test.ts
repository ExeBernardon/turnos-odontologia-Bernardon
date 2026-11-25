import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorConexion } from '../../api/cliente';
import type { Bloqueo, HorarioAtencion, Turno } from '../../api/types';
import { ejemplo } from '../configuracion/ejemplos.testutil';
import { cargarDatosAgenda, type DatosAgenda } from './agenda.api';
import { render } from './agenda';

vi.mock('./agenda.api', () => ({ cargarDatosAgenda: vi.fn() }));

const cargar = vi.mocked(cargarDatosAgenda);
const turnos = ejemplo('turnos-listar.200') as Turno[];

function vacio(): DatosAgenda {
  return { turnos: [], bloqueos: [], horarios: [], errores: [] };
}

function montar(): HTMLElement {
  document.body.innerHTML = '<div id="app"></div>';
  const contenedor = document.getElementById('app') as HTMLElement;
  window.history.replaceState({}, '', '/admin/agenda');
  render(contenedor, { ruta: '/agenda', query: new URLSearchParams() });
  return contenedor;
}

function boton(contenedor: HTMLElement, texto: string): HTMLButtonElement {
  const encontrado = [...contenedor.querySelectorAll('button')].find(
    (b) => b.textContent === texto || b.getAttribute('aria-label') === texto,
  );
  expect(encontrado).toBeInstanceOf(HTMLButtonElement);
  return encontrado as HTMLButtonElement;
}

describe('pantalla de agenda', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-12-01T12:00:00Z'));
    cargar.mockReset();
    cargar.mockResolvedValue(vacio());
  });

  afterEach(() => {
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  it('muestra la tarjeta diaria con todos los datos y oculta cancelados hasta el toggle', async () => {
    const turnosDelDia: Turno[] = [
      turnos[0] as Turno,
      { ...(turnos[1] as Turno), inicio: '2026-12-01T10:00:00-03:00', fin: '2026-12-01T10:30:00-03:00' },
      { ...(turnos[2] as Turno), inicio: '2026-12-01T15:00:00-03:00', fin: '2026-12-01T15:30:00-03:00' },
    ];
    cargar.mockResolvedValue({ ...vacio(), turnos: turnosDelDia });
    const contenedor = montar();
    await vi.waitFor(() => expect(contenedor.textContent).toContain('Bruno Prueba'));

    const urgente = contenedor.querySelector<HTMLElement>('[data-turno-id="102"]');
    expect(urgente).not.toBeNull();
    expect(urgente?.textContent).toContain('Limpieza');
    expect(urgente?.textContent).toContain('16:00 – 16:45');
    expect(urgente?.textContent).toContain('Sin confirmar · menos de 24 h');
    expect(urgente?.textContent).toContain('+54 9 11 0000-0001');
    expect(urgente?.className).toContain('agenda-turno--urgente');
    expect(contenedor.textContent).toContain('Ana Ejemplo');
    expect(contenedor.querySelector('[data-turno-id="104"]')).toBeNull();
    expect(contenedor.querySelector('[role="status"]')?.textContent).toContain('menos de 24 h');

    const confirmar = urgente?.querySelector<HTMLButtonElement>('[data-enganche="confirmar"]');
    const cancelar = urgente?.querySelector<HTMLButtonElement>('[data-enganche="cancelar"]');
    expect(confirmar?.disabled).toBe(true);
    expect(cancelar?.disabled).toBe(true);
    const llamadas = cargar.mock.calls.length;
    confirmar?.click();
    cancelar?.click();
    expect(cargar.mock.calls.length).toBe(llamadas);

    boton(contenedor, 'Mostrar cancelados').click();
    await vi.waitFor(() => expect(contenedor.querySelector('[data-turno-id="104"]')).not.toBeNull());
    expect(contenedor.querySelector('[data-turno-id="104"]')?.className).toContain('agenda-turno--cancelado');
    expect(boton(contenedor, 'Mostrar cancelados').getAttribute('aria-pressed')).toBe('true');
  });

  it('cambia de día, de semana y vuelve al periodo actual consultando el rango correcto', async () => {
    const contenedor = montar();
    await vi.waitFor(() => expect(cargar).toHaveBeenCalledTimes(1));
    expect(cargar.mock.calls[0]?.[0]).toEqual({ desde: '2026-12-01', hasta: '2026-12-01' });

    boton(contenedor, 'Día siguiente').click();
    await vi.waitFor(() => expect(cargar).toHaveBeenCalledTimes(2));
    expect(cargar.mock.calls[1]?.[0]).toEqual({ desde: '2026-12-02', hasta: '2026-12-02' });
    expect(contenedor.querySelector('time')?.textContent).toContain('2 de diciembre de 2026');

    boton(contenedor, 'Semana').click();
    await vi.waitFor(() => expect(cargar).toHaveBeenCalledTimes(3));
    expect(cargar.mock.calls[2]?.[0]).toEqual({ desde: '2026-11-30', hasta: '2026-12-06' });
    expect(boton(contenedor, 'Semana').getAttribute('aria-pressed')).toBe('true');
    expect(contenedor.querySelector('time')?.textContent).toContain('30 de noviembre de 2026');

    boton(contenedor, 'Semana siguiente').click();
    await vi.waitFor(() => expect(cargar).toHaveBeenCalledTimes(4));
    expect(cargar.mock.calls[3]?.[0]).toEqual({ desde: '2026-12-07', hasta: '2026-12-13' });

    boton(contenedor, 'Esta semana').click();
    await vi.waitFor(() => expect(cargar).toHaveBeenCalledTimes(5));
    expect(cargar.mock.calls[4]?.[0]).toEqual({ desde: '2026-11-30', hasta: '2026-12-06' });
  });

  it('distingue bloqueos y horarios fuera de atención, y permite reintentar errores', async () => {
    const bloqueos: Bloqueo[] = [
      {
        id: 1,
        inicio: '2026-12-01T11:00:00-03:00',
        fin: '2026-12-01T13:00:00-03:00',
        motivo: 'Capacitación',
        creadoEn: '2026-11-23T09:00:00-03:00',
      },
    ];
    const horarios: HorarioAtencion[] = [
      { id: 3, diaSemana: 2, horaInicio: '09:00', horaFin: '13:00' },
      { id: 4, diaSemana: 2, horaInicio: '15:00', horaFin: '19:00' },
    ];
    cargar.mockResolvedValue({ ...vacio(), bloqueos, horarios });
    const contenedor = montar();
    await vi.waitFor(() => expect(contenedor.textContent).toContain('Bloqueo: Capacitación'));
    expect(contenedor.textContent).toContain('08:00 – 09:00 · Fuera del horario de atención');
    expect(contenedor.querySelector('.agenda-franja--bloqueo')).not.toBeNull();

    cargar.mockRejectedValue(new ErrorConexion());
    const otro = montar();
    await vi.waitFor(() => expect(otro.querySelector('[role="alert"]')).not.toBeNull());
    expect(otro.querySelector('[role="alert"]')?.textContent).toContain('conectarnos');
    const reintentar = boton(otro, 'Reintentar');
    const llamadas = cargar.mock.calls.length;
    reintentar.click();
    await vi.waitFor(() => expect(cargar.mock.calls.length).toBe(llamadas + 1));
  });

  it('ofrece nombres accesibles y estados ARIA en los controles', async () => {
    const contenedor = montar();
    await vi.waitFor(() => expect(cargar).toHaveBeenCalledTimes(1));
    expect(contenedor.querySelector('[aria-label="Día anterior"]')).not.toBeNull();
    expect(contenedor.querySelector('[aria-label="Día siguiente"]')).not.toBeNull();
    expect(contenedor.querySelector('[role="group"][aria-label="Tipo de vista"]')).not.toBeNull();
    expect(boton(contenedor, 'Día').getAttribute('aria-pressed')).toBe('true');
    expect(boton(contenedor, 'Semana').getAttribute('aria-pressed')).toBe('false');
    expect(boton(contenedor, 'Mostrar cancelados').getAttribute('aria-pressed')).toBe('false');
    expect(contenedor.querySelector('[role="status"]')).not.toBeNull();
  });
});
