# Validadores del contrato de API

`docs/api/openapi.yaml` es la fuente de verdad y se edita a mano. `tipos.ts` y `ejemplos/` se mantienen sincronizados con él.

Antes de abrir un PR que toque el contrato:

```bash
cd docs/api/scripts
npm install
npm run verificar
```

| Script | Qué chequea |
|---|---|
| `lint` | El OpenAPI es válido (Redocly) |
| `tipos` | `tipos.ts` compila en modo estricto |
| `ejemplos` | Cada JSON de `ejemplos/` cumple el schema que lo referencia y no hay ejemplos sin referenciar |
| `coherencia` | Endpoints, matriz de `errores.md`, catálogo de códigos y `tipos.ts` coinciden entre sí; el resumen público no expone datos personales ni el token |
