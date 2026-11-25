import { pedir } from '../../api/cliente';
import type { HorarioAtencion, HorarioAtencionRequest } from '../../api/types';
import { esObjeto, leerNumero, leerTexto, parsearLista, sinContenido } from './parseo';

function parsearHorario(dato: unknown): HorarioAtencion {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: horario');
  }
  return {
    id: leerNumero(dato, 'id'),
    diaSemana: leerNumero(dato, 'diaSemana'),
    horaInicio: leerTexto(dato, 'horaInicio'),
    horaFin: leerTexto(dato, 'horaFin'),
  };
}

const RUTA = '/api/horarios-atencion';

export function listarHorarios(): Promise<HorarioAtencion[]> {
  return pedir('GET', RUTA, { parsear: (d) => parsearLista(d, parsearHorario) });
}

export function crearHorario(body: HorarioAtencionRequest): Promise<HorarioAtencion> {
  return pedir('POST', RUTA, { body, parsear: parsearHorario });
}

export function actualizarHorario(id: number, body: HorarioAtencionRequest): Promise<HorarioAtencion> {
  return pedir('PUT', RUTA + '/' + id, { body, parsear: parsearHorario });
}

export function eliminarHorario(id: number): Promise<void> {
  return pedir('DELETE', RUTA + '/' + id, { parsear: sinContenido });
}
