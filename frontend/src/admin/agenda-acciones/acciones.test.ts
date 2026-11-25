import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Turno } from '../../api/types';
import { ejemplo } from '../configuracion/ejemplos.testutil';
import { respuesta, stubServidor } from '../configuracion/servidor.testutil';
import { type AccionesAgenda, crearAccionesAgenda } from './acciones';

const turnos = ejemplo('turnos-listar.200') as Turno[];
const URGENTE = turnos[0] as Turno; // 102 PENDIENTE 16:00 (Bruno Prueba)
const CONFIRMADO = turnos[1] as Turno; // 103
const CANCELADO = turnos[2] as Turno; // 104
const ANTES = () => new Date('2026-12-01T12:00:00Z'); // 09:00 en Buenos Aires
const DESPUES = () => new Date('2026-12-01T20:00:00Z'); // 17:00 en Buenos Aires

let recargar: ReturnType<typeof vi.fn<() => Promise<void>>>;
let acciones: AccionesAgenda;

function montar(ahora: () => Date = ANTES): void {
  document.body.innerHTML = '<main id="raiz"></main>';
  recargar = vi.fn<() => Promise<void>>().mockResolvedValue(undefined);
  acciones = crearAccionesAgenda({ recargar, ahora });
  (document.getElementById('raiz') as HTMLElement).append(acciones.barra, acciones.aviso, acciones.alerta, acciones.panel);
}

function tarjeta(turno: Turno, compacta = false): HTMLElement {
  const articulo = document.createElement('article');
  articulo.setAttribute('data-turno-id', String(turno.id));
  const bloque = acciones.deTurno(turno, compacta);
  if (bloque !== null) {
    articulo.append(bloque);
  }
  document.getElementById('raiz')?.append(articulo);
  return articulo;
}

function botones(contenedor: ParentNode): HTMLButtonElement[] {
  return [...contenedor.querySelectorAll('button')];
}

function porEnganche(contenedor: ParentNode, nombre: string): HTMLButtonElement {
  const b = contenedor.querySelector<HTMLButtonElement>(`[data-enganche="${nombre}"]`);
  expect(b, 'enganche ' + nombre).toBeInstanceOf(HTMLButtonElement);
  return b as HTMLButtonElement;
}

function dialogo(): HTMLElement | null {
  return document.querySelector<HTMLElement>('[role="alertdialog"]');
}

function botonDialogo(nombre: string): HTMLButtonElement {
  const b = botones(dialogo() as HTMLElement).find((x) => x.textContent === nombre);
  expect(b, 'botón del diálogo ' + nombre).toBeInstanceOf(HTMLButtonElement);
  return b as HTMLButtonElement;
}

afterEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = '';
});

describe('crearAccionesAgenda: piezas', () => {
  beforeEach(() => montar());

  it('entrega la barra con "Nuevo turno" y las regiones de aviso y alerta', () => {
    expect(botones(acciones.barra).map((b) => b.textContent)).toEqual(['Nuevo turno']);
    expect(acciones.aviso.getAttribute('role')).toBe('status');
    expect(acciones.aviso.getAttribute('tabindex')).toBe('-1');
    expect(acciones.alerta.getAttribute('role')).toBe('alert');
    expect(acciones.panel.children).toHaveLength(0);
  });

  it('"Nuevo turno" abre el formulario de alta en el panel y reemplaza al anterior', () => {
    stubServidor({ 'GET /api/prestaciones': () => respuesta(200, []) });
    (acciones.barra.querySelector('button') as HTMLButtonElement).click();
    expect(acciones.panel.querySelector('h2')?.textContent).toBe('Nuevo turno');
    expect(acciones.panel.querySelectorAll('section')).toHaveLength(1);
  });
});

describe('deTurno: matriz de botones', () => {
  beforeEach(() => montar());

  it('para un urgente devuelve primero Confirmar y después Cancelar, con nombres accesibles', () => {
    const bloque = tarjeta(URGENTE);
    const [confirmar, cancelar] = botones(bloque);
    expect(confirmar?.getAttribute('data-enganche')).toBe('confirmar');
    expect(cancelar?.getAttribute('data-enganche')).toBe('cancelar');
    expect(confirmar?.textContent).toBe('Confirmar');
    expect(confirmar?.getAttribute('aria-label')).toBe('Confirmar turno de Bruno Prueba, 16:00');
    expect(cancelar?.getAttribute('aria-label')).toBe('Cancelar turno de Bruno Prueba, 16:00');
    expect(botones(bloque).map((b) => b.getAttribute('data-enganche'))).toEqual([
      'confirmar',
      'cancelar',
      'reprogramar',
      'asistio',
      'ausente',
    ]);
  });

  it('para un CONFIRMADO no incluye Confirmar', () => {
    const bloque = tarjeta(CONFIRMADO);
    expect(bloque.querySelector('[data-enganche="confirmar"]')).toBeNull();
    expect(bloque.querySelector('[data-enganche="cancelar"]')).not.toBeNull();
  });

  it('para los terminales devuelve null', () => {
    expect(acciones.deTurno(CANCELADO, false)).toBeNull();
    expect(acciones.deTurno({ ...CONFIRMADO, estado: 'ASISTIO' }, false)).toBeNull();
    expect(acciones.deTurno({ ...CONFIRMADO, estado: 'AUSENTE' }, true)).toBeNull();
  });

  it('en la vista compacta, Reprogramar y la asistencia van en "Más acciones"', () => {
    const bloque = tarjeta(URGENTE, true);
    const detalles = bloque.querySelector('details');
    expect(detalles?.querySelector('summary')?.textContent).toBe('Más acciones');
    const dentro = botones(detalles as HTMLElement).map((b) => b.getAttribute('data-enganche'));
    expect(dentro).toEqual(['reprogramar', 'asistio', 'ausente']);
    expect(detalles?.querySelector('[data-enganche="confirmar"]')).toBeNull();
    expect(tarjeta(URGENTE, false).querySelector('details')).toBeNull();
  });

  it('la asistencia queda deshabilitada antes del inicio con el motivo vinculado', () => {
    const bloque = tarjeta(URGENTE);
    const asistio = porEnganche(bloque, 'asistio');
    expect(asistio.disabled).toBe(true);
    expect(porEnganche(bloque, 'ausente').disabled).toBe(true);
    const motivo = document.getElementById(asistio.getAttribute('aria-describedby') ?? '');
    expect(motivo?.textContent).toBe('Se puede marcar desde las 16:00');
  });

  it('la asistencia se habilita pasada la hora de inicio y no muestra el motivo', () => {
    montar(DESPUES);
    const bloque = tarjeta(URGENTE);
    expect(porEnganche(bloque, 'asistio').disabled).toBe(false);
    expect(bloque.textContent).not.toContain('Se puede marcar desde');
    expect(bloque.querySelector('[data-enganche="confirmar"]')).toBeNull();
  });
});

describe('flujos de acción', () => {
  it('Confirmar: una sola solicitud ante un doble click, anuncia, recarga y enfoca la tarjeta', async () => {
    montar();
    const { llamadas } = stubServidor({
      'POST /api/turnos/102/confirmar': () => respuesta(200, ejemplo('turnos-confirmar.200')),
    });
    const articulo = tarjeta(URGENTE);
    const confirmar = porEnganche(articulo, 'confirmar');
    confirmar.click();
    confirmar.click();
    await vi.waitFor(() => expect(recargar).toHaveBeenCalledTimes(1));
    expect(llamadas.filter((l) => l.metodo === 'POST')).toHaveLength(1);
    expect(acciones.aviso.textContent).toBe('Turno confirmado');
    expect(document.activeElement).toBe(articulo);
    expect(dialogo()).toBeNull();
  });

  it('Confirmar: si el turno ya no está en la agenda, el foco va al aviso', async () => {
    montar();
    stubServidor({ 'POST /api/turnos/102/confirmar': () => respuesta(200, ejemplo('turnos-confirmar.200')) });
    const articulo = tarjeta(URGENTE);
    recargar.mockImplementation(() => {
      articulo.remove();
      return Promise.resolve();
    });
    porEnganche(articulo, 'confirmar').click();
    await vi.waitFor(() => expect(document.activeElement).toBe(acciones.aviso));
  });

  it('Cancelar: el diálogo nombra paciente, prestación, horario y email; "Volver" no llama a la API', async () => {
    montar();
    const { llamadas } = stubServidor({});
    const articulo = tarjeta(URGENTE);
    porEnganche(articulo, 'cancelar').click();
    const texto = dialogo()?.textContent ?? '';
    for (const dato of ['Bruno Prueba', 'Limpieza', 'martes 1 de diciembre, 16:00', 'bruno@ejemplo.com']) {
      expect(texto).toContain(dato);
    }
    botonDialogo('Volver').click();
    await Promise.resolve();
    expect(llamadas).toHaveLength(0);
    expect(dialogo()).toBeNull();
    expect(recargar).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(porEnganche(articulo, 'cancelar'));
  });

  it('Cancelar: aceptar envía, anuncia, recarga y deja el foco en el aviso si el turno ya no está', async () => {
    montar();
    const { llamadas } = stubServidor({
      'POST /api/turnos/102/cancelar': () => respuesta(200, ejemplo('turnos-cancelar.200')),
    });
    const articulo = tarjeta(URGENTE);
    recargar.mockImplementation(() => {
      articulo.remove();
      return Promise.resolve();
    });
    porEnganche(articulo, 'cancelar').click();
    botonDialogo('Cancelar turno').click();
    await vi.waitFor(() => expect(recargar).toHaveBeenCalledTimes(1));
    expect(llamadas[0]).toMatchObject({ metodo: 'POST', url: '/api/turnos/102/cancelar' });
    expect(acciones.aviso.textContent).toBe('Turno cancelado');
    await vi.waitFor(() => expect(document.activeElement).toBe(acciones.aviso));
  });

  it.each([
    ['asistio', 'ASISTIO', 'Asistencia registrada'],
    ['ausente', 'AUSENTE', 'Ausencia registrada'],
  ])('%s: pide confirmación y envía el PATCH con el estado correcto', async (enganche, estado, anuncio) => {
    montar(DESPUES);
    const { llamadas } = stubServidor({
      'PATCH /api/turnos/102/estado': () => respuesta(200, { ...(ejemplo('turnos-estado.200') as object), estado }),
    });
    const articulo = tarjeta(URGENTE);
    porEnganche(articulo, enganche).click();
    expect(dialogo()).not.toBeNull();
    botonDialogo(enganche === 'asistio' ? 'Marcar asistencia' : 'Marcar ausencia').click();
    await vi.waitFor(() => expect(recargar).toHaveBeenCalledTimes(1));
    expect(llamadas[0]?.body).toEqual({ estado });
    expect(acciones.aviso.textContent).toBe(anuncio);
  });

  it('un 422 TRANSICION_INVALIDA muestra la alerta y recarga la agenda', async () => {
    montar();
    stubServidor({
      'POST /api/turnos/102/confirmar': () => respuesta(422, ejemplo('turnos-confirmar.422-transicion-invalida')),
    });
    porEnganche(tarjeta(URGENTE), 'confirmar').click();
    await vi.waitFor(() => expect(recargar).toHaveBeenCalledTimes(1));
    expect(acciones.alerta.textContent).toBe('El turno ya no se puede confirmar. Actualizamos la agenda.');
    expect(acciones.aviso.textContent).toBe('');
  });

  it('un error de conexión muestra el mensaje, no recarga y rehabilita los botones', async () => {
    montar();
    stubServidor({
      'POST /api/turnos/102/confirmar': () => {
        throw new TypeError('network');
      },
    });
    const articulo = tarjeta(URGENTE);
    porEnganche(articulo, 'confirmar').click();
    await vi.waitFor(() => expect(acciones.alerta.textContent).toContain('conectarnos'));
    expect(recargar).not.toHaveBeenCalled();
    expect(porEnganche(articulo, 'confirmar').disabled).toBe(false);
    expect(porEnganche(articulo, 'cancelar').disabled).toBe(false);
    expect(articulo.querySelector('[aria-busy="true"]')).toBeNull();
  });

  it('un 401 no muestra mensaje propio', async () => {
    montar();
    stubServidor({
      'POST /api/turnos/102/confirmar': () => respuesta(401, ejemplo('error.401-no-autenticado')),
    });
    porEnganche(tarjeta(URGENTE), 'confirmar').click();
    await vi.waitFor(() => expect(porEnganche(document.body, 'confirmar').disabled).toBe(false));
    expect(acciones.alerta.textContent).toBe('');
    expect(recargar).not.toHaveBeenCalled();
  });

  it('Reprogramar abre el panel con el turno y, al cerrarlo, devuelve el foco al botón', () => {
    montar();
    stubServidor({ 'GET /api/disponibilidad?prestacionId=2&desde=2026-12-01&hasta=2026-12-01': () => respuesta(200, {}) });
    const articulo = tarjeta(URGENTE);
    porEnganche(articulo, 'reprogramar').click();
    expect(acciones.panel.querySelector('h2')?.textContent).toBe('Reprogramar turno');
    (acciones.panel.querySelector('.aa-acciones button[type="button"]') as HTMLButtonElement).click();
    expect(acciones.panel.children).toHaveLength(0);
    expect(document.activeElement).toBe(porEnganche(articulo, 'reprogramar'));
  });
});
