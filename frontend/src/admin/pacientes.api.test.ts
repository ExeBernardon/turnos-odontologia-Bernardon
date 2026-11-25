import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '../api/cliente';
import type { PacienteRequest } from '../api/types';
import { ejemplo } from './configuracion/ejemplos.testutil';
import { respuesta, stubServidor } from './configuracion/servidor.testutil';
import { actualizarPaciente, buscarPacientes, crearPaciente, obtenerPaciente } from './pacientes.api';

afterEach(() => {
  vi.unstubAllGlobals();
});

function peticion(nombre: string): PacienteRequest {
  return ejemplo(nombre) as PacienteRequest;
}

describe('buscarPacientes', () => {
  it('sin texto llama GET /api/pacientes sin q y parsea con y sin ultimoContacto', async () => {
    const { llamadas } = stubServidor({ 'GET /api/pacientes': () => respuesta(200, ejemplo('pacientes-listar.200')) });
    const lista = await buscarPacientes('');
    expect(llamadas.map((l) => l.url)).toEqual(['/api/pacientes']);
    expect(lista).toHaveLength(2);
    expect(lista[0]?.ultimoContacto).toEqual({ email: 'paciente@ejemplo.com', telefono: '+54 9 11 0000-0000' });
    expect(lista[1]?.ultimoContacto).toBeNull();
  });

  it('recorta y codifica q', async () => {
    const { llamadas } = stubServidor({ 'GET /api/pacientes?q=Muestra%20Prueba': () => respuesta(200, []) });
    await buscarPacientes(' Muestra Prueba ');
    expect(llamadas.map((l) => l.url)).toEqual(['/api/pacientes?q=Muestra%20Prueba']);
  });

  it('solo espacios equivale a busqueda vacia', async () => {
    const { llamadas } = stubServidor({ 'GET /api/pacientes': () => respuesta(200, []) });
    await buscarPacientes('   ');
    expect(llamadas.map((l) => l.url)).toEqual(['/api/pacientes']);
  });

  it('codifica el DNI con puntos', async () => {
    const { llamadas } = stubServidor({ 'GET /api/pacientes?q=99.999': () => respuesta(200, []) });
    await buscarPacientes('99.999');
    expect(llamadas[0]?.url).toBe('/api/pacientes?q=99.999');
  });
});

describe('obtenerPaciente', () => {
  it('parsea el ejemplo del contrato', async () => {
    stubServidor({ 'GET /api/pacientes/1': () => respuesta(200, ejemplo('pacientes-obtener.200')) });
    const p = await obtenerPaciente(1);
    expect(p).toMatchObject({ id: 1, nombre: 'Ana', apellido: 'Ejemplo', dni: '99999999' });
    expect(p.ultimoContacto?.email).toBe('paciente@ejemplo.com');
  });

  it('propaga el 404 como ErrorApi', async () => {
    stubServidor({
      'GET /api/pacientes/9': () =>
        respuesta(404, {
          timestamp: '2026-11-30T10:15:00-03:00',
          status: 404,
          error: 'Not Found',
          codigo: 'RECURSO_NO_ENCONTRADO',
          mensaje: 'No existe',
          path: '/api/pacientes/9',
        }),
    });
    await expect(obtenerPaciente(9)).rejects.toMatchObject({ status: 404, codigo: 'RECURSO_NO_ENCONTRADO' });
  });
});

describe('crearPaciente', () => {
  it('envia el body y parsea el 201', async () => {
    const body = peticion('pacientes-crear.request');
    const { llamadas } = stubServidor({ 'POST /api/pacientes': () => respuesta(201, ejemplo('pacientes-crear.201')) });
    const p = await crearPaciente(body);
    expect(llamadas[0]?.body).toEqual(body);
    expect(p.dni).toBe('99999997');
  });

  it('el 422 DNI_DUPLICADO llega como ErrorApi', async () => {
    stubServidor({ 'POST /api/pacientes': () => respuesta(422, ejemplo('pacientes-crear.422-dni-duplicado')) });
    const error: unknown = await crearPaciente(peticion('pacientes-crear.request')).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ErrorApi);
    expect(error).toMatchObject({ status: 422, codigo: 'DNI_DUPLICADO' });
  });
});

describe('actualizarPaciente', () => {
  it('hace PUT /api/pacientes/3 con el body y parsea el 200', async () => {
    const body = peticion('pacientes-actualizar.request');
    const { llamadas } = stubServidor({ 'PUT /api/pacientes/3': () => respuesta(200, ejemplo('pacientes-actualizar.200')) });
    const p = await actualizarPaciente(3, body);
    expect(llamadas[0]).toMatchObject({ metodo: 'PUT', body });
    expect(p.apellido).toBe('Muestra Prueba');
  });

  it('el 422 DNI_DUPLICADO llega como ErrorApi', async () => {
    stubServidor({ 'PUT /api/pacientes/3': () => respuesta(422, ejemplo('pacientes-actualizar.422-dni-duplicado')) });
    await expect(actualizarPaciente(3, peticion('pacientes-actualizar.request'))).rejects.toMatchObject({
      codigo: 'DNI_DUPLICADO',
    });
  });
});

describe('parseo defensivo', () => {
  it.each([
    ['sin ultimoContacto', { id: 1, nombre: 'Ana', apellido: 'E', dni: '99999999' }],
    ['id como texto', { id: '1', nombre: 'Ana', apellido: 'E', dni: '99999999', ultimoContacto: null }],
    ['contacto sin telefono', { id: 1, nombre: 'Ana', apellido: 'E', dni: '99999999', ultimoContacto: { email: 'a@ejemplo.com' } }],
    ['ultimoContacto texto', { id: 1, nombre: 'Ana', apellido: 'E', dni: '99999999', ultimoContacto: 'x' }],
  ])('rechaza una respuesta %s', async (_nombre, cuerpo) => {
    stubServidor({ 'GET /api/pacientes/1': () => respuesta(200, cuerpo) });
    await expect(obtenerPaciente(1)).rejects.toThrow('Respuesta inválida');
  });

  it('rechaza una lista con un item mal formado', async () => {
    stubServidor({ 'GET /api/pacientes': () => respuesta(200, [{ id: 1 }]) });
    await expect(buscarPacientes('')).rejects.toThrow('Respuesta inválida');
  });
});
