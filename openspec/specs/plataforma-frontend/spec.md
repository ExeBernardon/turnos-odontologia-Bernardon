# plataforma-frontend Specification

## Purpose

Define la base transversal del front vanilla que todas las pantallas comparten: las dos áreas con su entrada, el ruteo de todas las pantallas del MVP, el cliente HTTP tipado contra el contrato, la sincronización de tipos con `docs/api/` y la infraestructura de tests.

## Requirements

### Requirement: Todas las rutas del MVP registradas
El front SHALL resolver estas rutas a su pantalla: área pública `/reservar`, `/turno/confirmar`, `/turno/cancelar`; área privada `/admin/login`, `/admin/agenda`, `/admin/configuracion`, `/admin/pacientes`. Mientras una pantalla no esté implementada SHALL mostrar un placeholder con su nombre.

#### Scenario: Pantalla placeholder
- **WHEN** se navega a `/admin/pacientes` antes de que exista la pantalla real
- **THEN** se ve un placeholder que identifica la pantalla de pacientes

#### Scenario: Raíz de cada área
- **WHEN** se navega a `/` o a `/admin/`
- **THEN** se muestra `/reservar` en el área pública y `/admin/agenda` en la privada, respectivamente

#### Scenario: Ruta desconocida
- **WHEN** se navega a una ruta que no está registrada dentro de un área
- **THEN** se muestra un mensaje de página no encontrada, sin error en consola

### Requirement: Navegación sin recargar la página
Dentro de un área, seguir un link interno SHALL cambiar la URL y la pantalla sin recargar el documento, y los botones atrás y adelante del navegador SHALL mostrar la pantalla de la URL correspondiente. La query string SHALL quedar disponible para la pantalla.

#### Scenario: Link interno
- **WHEN** en el área privada se sigue un link a `/admin/configuracion`
- **THEN** la URL cambia y se muestra la pantalla de configuración sin recarga completa

#### Scenario: Botón atrás
- **WHEN** después de navegar de `/admin/agenda` a `/admin/pacientes` se presiona atrás
- **THEN** se vuelve a mostrar la agenda

#### Scenario: Token en la query
- **WHEN** se abre `/turno/cancelar?token=abc`
- **THEN** la pantalla de cancelar recibe el valor `abc` del parámetro `token`

### Requirement: Configuración partida en secciones independientes
La pantalla `/admin/configuracion` SHALL componerse de tres secciones (prestaciones, horarios de atención y bloqueos) definidas en unidades separadas, de modo que cada una se pueda implementar sin modificar las otras.

#### Scenario: Tres secciones
- **WHEN** se navega a `/admin/configuracion`
- **THEN** se ven las secciones de prestaciones, horarios y bloqueos, cada una con su encabezado

### Requirement: Cliente HTTP con error uniforme
El front SHALL hablar con la API solo por `/api` relativo al mismo origen, enviar JSON, adjuntar `Authorization: Bearer` cuando hay sesión, tratar el cuerpo recibido como dato no confiable hasta validarlo y, ante una respuesta de error con el formato uniforme, entregar a la pantalla `status`, `codigo`, `mensaje` y `detalles` tipados.

#### Scenario: Error de negocio
- **WHEN** la API responde 409 con `codigo` `HORARIO_OCUPADO`
- **THEN** el llamador recibe un error con status 409, `codigo` `HORARIO_OCUPADO` y el `mensaje` del backend, sin parsear texto

#### Scenario: Respuesta de error sin el formato esperado
- **WHEN** la API o el proxy responden con un error cuyo cuerpo no es el JSON uniforme
- **THEN** el llamador recibe un error con el status HTTP y un mensaje genérico en español

#### Scenario: Con sesión
- **WHEN** hay un token de sesión guardado y se hace un request
- **THEN** el request lleva `Authorization: Bearer <token>`

#### Scenario: Falla de red
- **WHEN** el request no llega al servidor
- **THEN** el llamador recibe un error de conexión distinguible de un error HTTP

### Requirement: Tipos del contrato sincronizados
Los tipos que usa el front para requests, responses y errores SHALL ser una copia exacta de `docs/api/tipos.ts`, y SHALL existir un comando para resincronizarla y un test que falle si la copia difiere del original.

#### Scenario: Copia desactualizada
- **WHEN** alguien modifica `docs/api/tipos.ts` y no resincroniza la copia del front
- **THEN** los tests del front fallan indicando que los tipos están desincronizados

#### Scenario: Resincronización
- **WHEN** se ejecuta el comando de sincronización de tipos
- **THEN** la copia del front queda idéntica a `docs/api/tipos.ts` y el test pasa

### Requirement: TypeScript estricto sin any
El código del front SHALL compilar con TypeScript en modo `strict` y el build SHALL fallar si hay errores de tipo; el linter SHALL rechazar cualquier uso explícito de `any`. No SHALL incluirse ningún framework de UI.

#### Scenario: Error de tipos rompe el build
- **WHEN** se introduce un error de tipos y se ejecuta `npm run build`
- **THEN** el build falla

#### Scenario: any explícito
- **WHEN** se escribe una anotación `: any` y se ejecuta `npm run lint`
- **THEN** el linter reporta error

#### Scenario: Sin framework de UI
- **WHEN** se revisan las dependencias de `frontend/package.json`
- **THEN** no hay React, Vue, Angular, Svelte ni otro framework de UI

### Requirement: Tests del front con Vitest
El front SHALL tener Vitest configurado con entorno DOM y `npm test` SHALL correr la suite una vez y terminar con código distinto de cero si algún test falla.

#### Scenario: Suite de humo
- **WHEN** se ejecuta `npm test` en `frontend/`
- **THEN** corren los tests del router, del cliente HTTP y de la sincronización de tipos y pasan
