import { pedir } from '../api/cliente';
import type { Contacto, Paciente, PacienteRequest } from '../api/types';
import { esObjeto, leerNumero, leerTexto, parsearLista } from './configuracion/parseo';

function parsearContacto(dato: unknown): Contacto {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: ultimoContacto');
  }
  return { email: leerTexto(dato, 'email'), telefono: leerTexto(dato, 'telefono') };
}

/** Valida una respuesta `unknown` como `Paciente`; `ultimoContacto` debe estar presente (objeto o null). */
export function parsearPaciente(dato: unknown): Paciente {
  if (!esObjeto(dato)) {
    throw new Error('Respuesta inválida: paciente');
  }
  if (!('ultimoContacto' in dato)) {
    throw new Error('Respuesta inválida: ultimoContacto');
  }
  const contacto = dato['ultimoContacto'];
  return {
    id: leerNumero(dato, 'id'),
    nombre: leerTexto(dato, 'nombre'),
    apellido: leerTexto(dato, 'apellido'),
    dni: leerTexto(dato, 'dni'),
    ultimoContacto: contacto === null ? null : parsearContacto(contacto),
  };
}

const RUTA = '/api/pacientes';

/** Lista pacientes; `q` recortado filtra por DNI o apellido, vacio devuelve todos. */
export function buscarPacientes(q: string): Promise<Paciente[]> {
  const texto = q.trim();
  const ruta = texto === '' ? RUTA : RUTA + '?q=' + encodeURIComponent(texto);
  return pedir('GET', ruta, { parsear: (d) => parsearLista(d, parsearPaciente) });
}

export function obtenerPaciente(id: number): Promise<Paciente> {
  return pedir('GET', RUTA + '/' + id, { parsear: parsearPaciente });
}

export function crearPaciente(body: PacienteRequest): Promise<Paciente> {
  return pedir('POST', RUTA, { body, parsear: parsearPaciente });
}

export function actualizarPaciente(id: number, body: PacienteRequest): Promise<Paciente> {
  return pedir('PUT', RUTA + '/' + id, { body, parsear: parsearPaciente });
}
