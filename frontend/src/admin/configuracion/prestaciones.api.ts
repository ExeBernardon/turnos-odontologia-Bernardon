import { pedir } from '../../api/cliente';
import type { Prestacion, PrestacionRequest } from '../../api/types';
import { esObjeto, leerBooleano, leerNumero, leerTexto, parsearLista, sinContenido } from './parseo';

function parsearPrestacion(dato: unknown): Prestacion {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: prestación');
  }
  return {
    id: leerNumero(dato, 'id'),
    nombre: leerTexto(dato, 'nombre'),
    duracionMinutos: leerNumero(dato, 'duracionMinutos'),
    activa: leerBooleano(dato, 'activa'),
  };
}

const RUTA = '/api/prestaciones';

export function listarPrestaciones(): Promise<Prestacion[]> {
  return pedir('GET', RUTA, { parsear: (d) => parsearLista(d, parsearPrestacion) });
}

export function crearPrestacion(body: PrestacionRequest): Promise<Prestacion> {
  return pedir('POST', RUTA, { body, parsear: parsearPrestacion });
}

export function actualizarPrestacion(id: number, body: PrestacionRequest): Promise<Prestacion> {
  return pedir('PUT', RUTA + '/' + id, { body, parsear: parsearPrestacion });
}

export function eliminarPrestacion(id: number): Promise<void> {
  return pedir('DELETE', RUTA + '/' + id, { parsear: sinContenido });
}
