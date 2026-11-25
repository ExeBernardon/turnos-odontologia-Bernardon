export interface EntradaPrestacion {
  readonly nombre: string;
  readonly duracion: string;
}

export interface ValorPrestacion {
  readonly nombre: string;
  readonly duracionMinutos: number;
}

export type ErroresPrestacion = Partial<Record<'nombre' | 'duracionMinutos', string>>;

export type ResultadoPrestacion =
  | { readonly ok: true; readonly valor: ValorPrestacion }
  | { readonly ok: false; readonly errores: ErroresPrestacion };

export const NOMBRE_MAX = 100;
export const DURACION_MAX = 480;

/** Valida el formulario segun el contrato: nombre 1..100, duracion entera 1..480. */
export function validarPrestacion(entrada: EntradaPrestacion): ResultadoPrestacion {
  const errores: { nombre?: string; duracionMinutos?: string } = {};
  const nombre = entrada.nombre.trim();
  if (nombre === '') {
    errores.nombre = 'El nombre es obligatorio.';
  } else if (nombre.length > NOMBRE_MAX) {
    errores.nombre = `El nombre no puede superar los ${NOMBRE_MAX} caracteres.`;
  }

  const texto = entrada.duracion.trim();
  const duracion = /^\d+$/.test(texto) ? Number(texto) : Number.NaN;
  if (!Number.isInteger(duracion) || duracion < 1 || duracion > DURACION_MAX) {
    errores.duracionMinutos = `La duración debe ser un número entero de minutos entre 1 y ${DURACION_MAX}.`;
  }

  if (errores.nombre !== undefined || errores.duracionMinutos !== undefined) {
    return { ok: false, errores };
  }
  return { ok: true, valor: { nombre, duracionMinutos: duracion } };
}
