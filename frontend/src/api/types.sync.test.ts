import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const leer = (ruta: string): string => readFileSync(resolve(__dirname, ruta), 'utf8');

describe('tipos del contrato', () => {
  it('src/api/types.ts es copia exacta de docs/api/tipos.ts', () => {
    expect(leer('./types.ts'), 'tipos desincronizados: ejecutar `npm run tipos:sync`').toBe(
      leer('../../../docs/api/tipos.ts'),
    );
  });

  it('incluye el codigo ERROR_INTERNO del contrato', () => {
    expect(leer('./types.ts')).toContain("'ERROR_INTERNO'");
  });
});
