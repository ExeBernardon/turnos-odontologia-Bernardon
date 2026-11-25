import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/** Lee un ejemplo JSON del contrato (docs/api/ejemplos/) para usarlo como mock (regla 11). */
export function ejemplo(nombre: string): unknown {
  const ruta = resolve(__dirname, '../../../../docs/api/ejemplos', `${nombre}.json`);
  return JSON.parse(readFileSync(ruta, 'utf8')) as unknown;
}
