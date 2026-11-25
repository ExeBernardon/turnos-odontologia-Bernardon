import type { PacienteRequest } from '../api/types';

export interface EntradaPaciente {
  readonly nombre: string;
  readonly apellido: string;
  readonly dni: string;
}

export type ErroresPaciente = Partial<Record<keyof EntradaPaciente, string>>;

export const NOMBRE_MAX = 100;

/** Misma regla que `DniEntrada` del contrato: 7 u 8 digitos, o con puntos (d.ddd.ddd / dd.ddd.ddd). */
const DNI_VALIDO = /^(\d{7,8}|\d{1,2}\.\d{3}\.\d{3})$/;

function validarNombre(valor: string, etiqueta: string): string | undefined {
  const texto = valor.trim();
  if (texto === '') {
    return `${etiqueta} es obligatorio.`;
  }
  if (texto.length > NOMBRE_MAX) {
    return `${etiqueta} no puede superar los ${NOMBRE_MAX} caracteres.`;
  }
  return undefined;
}

/** Valida el formulario segun el contrato; devuelve un mensaje por campo con error (vacio si es valido). */
export function validarPaciente(entrada: EntradaPaciente): ErroresPaciente {
  const errores: { nombre?: string; apellido?: string; dni?: string } = {};
  const nombre = validarNombre(entrada.nombre, 'El nombre');
  if (nombre !== undefined) {
    errores.nombre = nombre;
  }
  const apellido = validarNombre(entrada.apellido, 'El apellido');
  if (apellido !== undefined) {
    errores.apellido = apellido;
  }
  if (!DNI_VALIDO.test(entrada.dni.trim())) {
    errores.dni = 'El DNI debe tener 7 u 8 dígitos, con o sin puntos (ej. 12.345.678).';
  }
  return errores;
}

/** Arma el body del contrato: valores recortados; el DNI va como se escribio (el backend lo normaliza). */
export function armarRequest(entrada: EntradaPaciente): PacienteRequest {
  return { nombre: entrada.nombre.trim(), apellido: entrada.apellido.trim(), dni: entrada.dni.trim() };
}

/** Muestra el DNI guardado (solo digitos) con puntos: 99999997 -> 99.999.997. */
export function formatearDni(dni: string): string {
  if (!/^\d{7,8}$/.test(dni)) {
    return dni;
  }
  return dni.replace(/\B(?=(\d{3})+$)/g, '.');
}
