import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorConexion } from '../api/cliente';
import type { Paciente } from '../api/types';
import { crearBuscadorPacientes } from './buscador-pacientes';

const ANA: Paciente = {
  id: 1,
  nombre: 'Ana',
  apellido: 'Ejemplo',
  dni: '99999999',
  ultimoContacto: { email: 'paciente@ejemplo.com', telefono: '+54 9 11 0000-0000' },
};
const CARLA: Paciente = { id: 3, nombre: 'Carla', apellido: 'Muestra', dni: '99999997', ultimoContacto: null };

beforeEach(() => {
  document.body.replaceChildren();
});

type Buscar = (q: string) => Promise<readonly Paciente[]>;

/** Deja correr las promesas pendientes. */
async function asentar(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
}

function montar(opciones: { buscar?: Buscar; prefijo?: string; textoSeleccionar?: string; etiqueta?: string } = {}) {
  const buscar = vi.fn<Buscar>(opciones.buscar ?? (() => Promise.resolve([ANA, CARLA])));
  const alSeleccionar = vi.fn<(p: Paciente) => void>();
  const buscador = crearBuscadorPacientes({
    prefijo: opciones.prefijo ?? 'bp',
    buscar,
    alSeleccionar,
    ...(opciones.textoSeleccionar === undefined ? {} : { textoSeleccionar: opciones.textoSeleccionar }),
    ...(opciones.etiqueta === undefined ? {} : { etiqueta: opciones.etiqueta }),
  });
  document.body.append(buscador.raiz);
  return { buscador, buscar, alSeleccionar, raiz: buscador.raiz };
}

function campo(raiz: HTMLElement): HTMLInputElement {
  const input = raiz.querySelector('input');
  if (input === null) {
    throw new Error('sin campo');
  }
  return input;
}

function enviar(raiz: HTMLElement): void {
  raiz.querySelector('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
}

function boton(raiz: HTMLElement, nombre: string): HTMLButtonElement {
  const b = Array.from(raiz.querySelectorAll('button')).find((x) => (x.getAttribute('aria-label') ?? x.textContent) === nombre);
  if (b === undefined) {
    throw new Error('boton no encontrado: ' + nombre);
  }
  return b;
}

describe('buscador: envio y texto', () => {
  it('tiene un label visible asociado al campo y un formulario de busqueda', () => {
    const { raiz } = montar();
    const label = raiz.querySelector('label');
    expect(label?.textContent).toBe('Buscar por DNI o apellido');
    expect(label?.control).toBe(campo(raiz));
    expect(raiz.querySelector('form')?.getAttribute('role')).toBe('search');
  });

  it('usa la etiqueta personalizada', () => {
    const { raiz } = montar({ etiqueta: 'Paciente' });
    expect(raiz.querySelector('label')?.textContent).toBe('Paciente');
  });

  it('el envio busca el texto recortado', async () => {
    const { raiz, buscar } = montar();
    campo(raiz).value = '  Ejem ';
    enviar(raiz);
    await asentar();
    expect(buscar).toHaveBeenCalledTimes(1);
    expect(buscar).toHaveBeenCalledWith('Ejem');
  });

  it('el boton Buscar es de tipo submit (Enter y clic envian)', () => {
    const { raiz } = montar();
    expect(boton(raiz, 'Buscar').type).toBe('submit');
  });

  it.each(['', '   '])('texto vacio (%j) busca con cadena vacia', async (texto) => {
    const { raiz, buscar } = montar();
    campo(raiz).value = texto;
    enviar(raiz);
    await asentar();
    expect(buscar).toHaveBeenCalledWith('');
  });

  it('buscar(q) programatico usa q y buscar() repite la ultima busqueda', async () => {
    const { buscador, buscar } = montar();
    await buscador.buscar('Mu');
    await buscador.buscar();
    expect(buscar.mock.calls).toEqual([['Mu'], ['Mu']]);
  });
});

describe('buscador: resultados y seleccion', () => {
  it('muestra los resultados en el orden recibido con Apellido, Nombre y DNI con puntos', async () => {
    const { buscador, raiz } = montar();
    await buscador.buscar('');
    const items = raiz.querySelectorAll('li');
    expect(items).toHaveLength(2);
    expect(items[0]?.textContent).toContain('Ejemplo, Ana');
    expect(items[0]?.textContent).toContain('99.999.999');
    expect(items[1]?.textContent).toContain('Muestra, Carla');
    expect(items[1]?.textContent).toContain('99.999.997');
  });

  it('el boton lleva aria-label con el nombre del paciente y el texto por defecto Ver', async () => {
    const { buscador, raiz } = montar();
    await buscador.buscar('');
    const b = boton(raiz, 'Ver Muestra, Carla');
    expect(b.textContent).toBe('Ver');
  });

  it('el texto del boton es personalizable', async () => {
    const { buscador, raiz } = montar({ textoSeleccionar: 'Elegir' });
    await buscador.buscar('');
    expect(boton(raiz, 'Elegir Ejemplo, Ana').textContent).toBe('Elegir');
  });

  it('activar el boton notifica el paciente y no vuelve a buscar', async () => {
    const { buscador, raiz, buscar, alSeleccionar } = montar();
    await buscador.buscar('');
    buscar.mockClear();
    boton(raiz, 'Ver Muestra, Carla').click();
    expect(alSeleccionar).toHaveBeenCalledTimes(1);
    expect(alSeleccionar).toHaveBeenCalledWith(CARLA);
    expect(buscar).not.toHaveBeenCalled();
  });

  it('una busqueda nueva reemplaza la lista anterior', async () => {
    const respuestas: Paciente[][] = [[ANA, CARLA], [CARLA]];
    const { buscador, raiz } = montar({ buscar: () => Promise.resolve(respuestas.shift() ?? []) });
    await buscador.buscar('');
    await buscador.buscar('Mu');
    expect(raiz.querySelectorAll('li')).toHaveLength(1);
  });
});

describe('buscador: estados', () => {
  function estado(raiz: HTMLElement): string {
    return raiz.querySelector('[role="status"]')?.textContent ?? '';
  }
  function alerta(raiz: HTMLElement): string {
    return raiz.querySelector('[role="alert"]')?.textContent ?? '';
  }

  it('anuncia Buscando mientras espera y la cantidad al terminar', async () => {
    let resolver: (p: readonly Paciente[]) => void = () => undefined;
    const { buscador, raiz } = montar({ buscar: () => new Promise((r) => (resolver = r)) });
    const pendiente = buscador.buscar('');
    expect(estado(raiz)).toBe('Buscando…');
    resolver([ANA, CARLA]);
    await pendiente;
    expect(estado(raiz)).toBe('2 pacientes encontrados');
  });

  it('anuncia singular con un resultado', async () => {
    const { buscador, raiz } = montar({ buscar: () => Promise.resolve([ANA]) });
    await buscador.buscar('');
    expect(estado(raiz)).toBe('1 paciente encontrado');
  });

  it('anuncia que no hay coincidencias con lista vacia', async () => {
    const { buscador, raiz } = montar({ buscar: () => Promise.resolve([]) });
    await buscador.buscar('zzz');
    expect(estado(raiz)).toBe('No hay pacientes que coincidan con la búsqueda.');
    expect(raiz.querySelectorAll('li')).toHaveLength(0);
  });

  it('error de conexion: alerta con el mensaje y Reintentar repite la ultima busqueda', async () => {
    const buscar = vi
      .fn<Buscar>()
      .mockRejectedValueOnce(new ErrorConexion())
      .mockResolvedValueOnce([CARLA]);
    const { buscador, raiz } = montar({ buscar });
    await buscador.buscar('Mu');
    expect(alerta(raiz)).toBe(new ErrorConexion().message);
    boton(raiz, 'Reintentar').click();
    await asentar();
    expect(buscar).toHaveBeenLastCalledWith('Mu');
    expect(buscar).toHaveBeenCalledTimes(2);
    expect(alerta(raiz)).toBe('');
    expect(estado(raiz)).toBe('1 paciente encontrado');
  });

  it('otro error muestra el mensaje generico sin detalles internos', async () => {
    const { buscador, raiz } = montar({ buscar: () => Promise.reject(new Error('stack interno secreto')) });
    await buscador.buscar('');
    expect(alerta(raiz)).toBe('No se pudo completar la operación. Intentá de nuevo en unos minutos.');
    expect(raiz.textContent).not.toContain('secreto');
  });
});

describe('buscador: respuestas fuera de orden', () => {
  it('descarta la respuesta vieja que llega despues de la nueva', async () => {
    const resolvers = new Map<string, (p: readonly Paciente[]) => void>();
    const { buscador, raiz } = montar({ buscar: (q) => new Promise((r) => resolvers.set(q, r)) });
    const primera = buscador.buscar('Ej');
    const segunda = buscador.buscar('Mu');
    resolvers.get('Mu')?.([CARLA]);
    await segunda;
    resolvers.get('Ej')?.([ANA]);
    await primera;
    const items = raiz.querySelectorAll('li');
    expect(items).toHaveLength(1);
    expect(items[0]?.textContent).toContain('Muestra, Carla');
    expect(raiz.querySelector('[role="status"]')?.textContent).toBe('1 paciente encontrado');
  });

  it('descarta tambien un error viejo que llega tarde', async () => {
    const pendientes = new Map<string, { ok: (p: readonly Paciente[]) => void; mal: (e: unknown) => void }>();
    const { buscador, raiz } = montar({
      buscar: (q) => new Promise((ok, mal) => pendientes.set(q, { ok, mal })),
    });
    const primera = buscador.buscar('Ej');
    const segunda = buscador.buscar('Mu');
    pendientes.get('Mu')?.ok([CARLA]);
    await segunda;
    pendientes.get('Ej')?.mal(new ErrorConexion());
    await primera;
    expect(raiz.querySelector('[role="alert"]')?.textContent).toBe('');
    expect(raiz.querySelectorAll('li')).toHaveLength(1);
  });
});

describe('buscador: instancias independientes', () => {
  it('dos instancias tienen ids unicos y cada label apunta a su campo', () => {
    const a = montar({ prefijo: 'uno' });
    const b = montar({ prefijo: 'dos' });
    const ca = campo(a.raiz);
    const cb = campo(b.raiz);
    expect(ca.id).not.toBe(cb.id);
    expect(a.raiz.querySelector('label')?.control).toBe(ca);
    expect(b.raiz.querySelector('label')?.control).toBe(cb);
    const ids = Array.from(document.querySelectorAll('[id]')).map((n) => n.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('enfocar() pone el foco en el campo', () => {
    const { buscador, raiz } = montar();
    buscador.enfocar();
    expect(document.activeElement).toBe(campo(raiz));
  });
});
