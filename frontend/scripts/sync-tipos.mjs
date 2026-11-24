// Copia exacta de docs/api/tipos.ts (fuente unica del contrato) a src/api/types.ts.
import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const origen = resolve(aqui, '../../docs/api/tipos.ts');
const destino = resolve(aqui, '../src/api/types.ts');

mkdirSync(dirname(destino), { recursive: true });
copyFileSync(origen, destino);
console.log(`tipos sincronizados: ${origen} -> ${destino}`);
