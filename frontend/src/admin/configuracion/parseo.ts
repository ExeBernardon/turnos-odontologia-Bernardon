/** Helpers de parseo de `unknown` a tipos del contrato (el cliente HTTP exige un parser por recurso). */

export function esObjeto(dato: unknown): dato is Record<string, unknown> {
  return typeof dato === 'object' && dato !== null && !Array.isArray(dato);
}

export function leerNumero(o: Record<string, unknown>, campo: string): number {
  const v = o[campo];
  if (typeof v !== 'number') {
    throw new Error('Respuesta inválida: ' + campo);
  }
  return v;
}

export function leerTexto(o: Record<string, unknown>, campo: string): string {
  const v = o[campo];
  if (typeof v !== 'string') {
    throw new Error('Respuesta inválida: ' + campo);
  }
  return v;
}

export function leerTextoONulo(o: Record<string, unknown>, campo: string): string | null {
  const v = o[campo];
  if (v !== null && typeof v !== 'string') {
    throw new Error('Respuesta inválida: ' + campo);
  }
  return v;
}

export function leerBooleano(o: Record<string, unknown>, campo: string): boolean {
  const v = o[campo];
  if (typeof v !== 'boolean') {
    throw new Error('Respuesta inválida: ' + campo);
  }
  return v;
}

export function parsearLista<T>(dato: unknown, item: (d: unknown) => T): T[] {
  if (!Array.isArray(dato)) {
    throw new Error('Respuesta inválida: se esperaba una lista');
  }
  return dato.map((d: unknown) => item(d));
}

export function sinContenido(): undefined {
  return undefined;
}
